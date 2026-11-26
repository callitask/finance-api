package com.treishvaam.financeapi.service;

import io.trbl.blurhash.BlurHash;
import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Centralized Enterprise Image Authority.
 *
 * <p>Scope: - Responsibilities: Ingestion, Security (Tika), Multi-variant Resizing, Optimization.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: OptimizationProfile enum for context-aware
 * compression (News vs Standard). - ADDED: processImage() method to handle byte[] ingestion
 * (decoupled from MultipartFile). - REFACTORED: Unified resizing logic to support both Blog
 * (Standard) and News (Aggressive) pipelines. - SECURITY: Tika validation enforced for all entry
 * points.
 *
 * <p>- EDITED (Phase 8 - Virtual Thread Race Condition Fix): • Wrapped the `Thumbnails.of`
 * execution inside `uploadResized` with `synchronized(ImageService.class)`. • Why: Resolves severe
 * `IndexOutOfBoundsException` in `FileCacheImageOutputStream.seek()` when Tomcat's Virtual Threads
 * attempt to process multi-variant images concurrently. Isolating the I/O write buffer ensures
 * stability.
 *
 * <p>- EDITED (Phase 8.1 - Explicit Audit): • Audited class-level synchronization lock to verify it
 * successfully defends against VirtualBox OOM panics during heavy concurrent CMS uploads.
 *
 * <p>- DO-NOT-DELETE RULE (ABSOLUTE): This IMMUTABLE CHANGE HISTORY section acts as the
 * institutional memory for future AI sessions. It must never be deleted, truncated, rewritten, or
 * regenerated. Future AI must append only.
 *
 * <p>- EDITED (2026-11-25 - Batch 1 §1.1 Heap OOM Closure): • Streamed MultipartFile uploads
 * directly to tempFile via Files.copy(InputStream, Path, REPLACE_EXISTING) in
 * saveImageAndGetMetadata() (eliminates file.getBytes() heap buffer). • Replaced 4x concurrent
 * CompletableFuture WebP resize tasks with sequential execution so each buffer is GC-eligible
 * immediately after MinIO upload. • Downscaled BlurHash source to 64x64 via Thumbnails.of(...)
 * under ImageService.class lock and called image.flush() in finally.
 */
@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
    private final FileStorageService fileStorageService;
    private final ResourceLoader resourceLoader;
    private final Tika tika;

    public ImageService(FileStorageService fileStorageService, ResourceLoader resourceLoader) {
        this.fileStorageService = fileStorageService;
        this.resourceLoader = resourceLoader;
        this.tika = new Tika();
    }

    public enum OptimizationProfile {
        STANDARD, // Blog Posts: Balanced quality (0.70 - 0.75)
        NEWS // News Feeds: Aggressive size reduction for high volume/throughput
    }

    public static class ImageMetadataDto {
        private String baseFilename;
        private Integer width;
        private Integer height;
        private String mimeType;
        private String blurHash;
        private String fullPath;

        public String getBaseFilename() {
            return baseFilename;
        }

        public void setBaseFilename(String baseFilename) {
            this.baseFilename = baseFilename;
        }

        public Integer getWidth() {
            return width;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public Integer getHeight() {
            return height;
        }

        public void setHeight(Integer height) {
            this.height = height;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getBlurHash() {
            return blurHash;
        }

        public void setBlurHash(String blurHash) {
            this.blurHash = blurHash;
        }

        public String getFullPath() {
            return fullPath;
        }

        public void setFullPath(String fullPath) {
            this.fullPath = fullPath;
        }
    }

    @PostConstruct
    public void init() {
        try {
            logger.info("Scanning for ImageIO plugins (WebP support)...");
            ImageIO.scanForPlugins();
            logger.info("ImageIO plugins registered successfully.");
        } catch (Exception e) {
            logger.error("Failed to scan ImageIO plugins", e);
        }
    }

    // --- ENTRY POINT 1: MultipartFile (Controllers) ---
    public ImageMetadataDto saveImageAndGetMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("img_proc_" + UUID.randomUUID(), ".tmp");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return processImageFromTempFile(tempFile, "upload_", OptimizationProfile.STANDARD);
        } catch (IOException e) {
            logger.error("Failed to stream upload to temp file", e);
            throw new RuntimeException("Failed to read upload stream", e);
        } finally {
            deleteTempFileQuietly(tempFile);
        }
    }

    // --- ENTRY POINT 2: Raw Bytes (Services like NewsHighlight) ---
    public ImageMetadataDto processImage(
            byte[] imageBytes, String prefix, OptimizationProfile profile) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("img_proc_" + UUID.randomUUID(), ".tmp");
            Files.write(tempFile, imageBytes);
            imageBytes = null;
            return processImageFromTempFile(tempFile, prefix, profile);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to process image", e);
            throw new RuntimeException("Image processing failed", e);
        } finally {
            deleteTempFileQuietly(tempFile);
        }
    }

    // --- CORE LOGIC ---
    private ImageMetadataDto processImageFromTempFile(
            Path tempFile, String prefix, OptimizationProfile profile) {
        try {
            // 2. Security Check: Validate MIME type using Apache Tika
            String detectedMime = tika.detect(tempFile.toFile());
            if (detectedMime == null || !detectedMime.startsWith("image/")) {
                logger.warn("Security Alert: Invalid image file type detected: {}", detectedMime);
                throw new SecurityException("Invalid file type: " + detectedMime);
            }

            // 3. Extract Metadata
            ImageMetadataDto metadata = extractMetadata(tempFile, detectedMime);

            // 4. Generate Filenames
            String baseUuid = UUID.randomUUID().toString();
            // Allow callers to inject prefixes (e.g. "news-mv-")
            String cleanPrefix = (prefix == null) ? "" : prefix;
            String baseFilename = cleanPrefix + baseUuid;

            metadata.setBaseFilename(baseFilename);

            // 5. Define Variants
            String masterName = baseFilename + ".webp"; // 1920w (Master)
            String desktopName = baseFilename + "-1200.webp"; // 1200w
            String tabletName = baseFilename + "-800.webp"; // 800w
            String mobileName = baseFilename + "-480.webp"; // 480w

            // 6. Define Quality Settings based on Profile
            double qMaster = (profile == OptimizationProfile.NEWS) ? 0.70 : 0.75;
            double qDesktop = (profile == OptimizationProfile.NEWS) ? 0.65 : 0.70;
            double qTablet = (profile == OptimizationProfile.NEWS) ? 0.55 : 0.60;
            double qMobile =
                    (profile == OptimizationProfile.NEWS) ? 0.45 : 0.50; // Aggressive for News

            // 7. Sequential Variant Execution (each buffer is GC-eligible after MinIO upload)
            uploadResizedSafe(tempFile, 1920, masterName, qMaster);
            uploadResizedSafe(tempFile, 1200, desktopName, qDesktop);
            uploadResizedSafe(tempFile, 800, tabletName, qTablet);
            uploadResizedSafe(tempFile, 480, mobileName, qMobile);

            // 8. Return the Master Path
            metadata.setFullPath(masterName);

            return metadata;

        } catch (Exception e) {
            logger.error("Failed to process image", e);
            throw new RuntimeException("Image processing failed", e);
        }
    }

    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                /* ignore */
            }
        }
    }

    private void uploadResizedSafe(
            Path sourceFile, int targetWidth, String filename, double quality) {
        try {
            uploadResized(sourceFile, targetWidth, filename, quality);
        } catch (IOException e) {
            throw new RuntimeException("Async upload failed for " + filename, e);
        }
    }

    private void uploadResized(Path sourceFile, int targetWidth, String filename, double quality)
            throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            try {
                // Prevent ImageIO/FileCache stream collisions across Virtual Threads
                synchronized (ImageService.class) {
                    Thumbnails.of(sourceFile.toFile())
                            .width(targetWidth)
                            .outputQuality(quality)
                            .outputFormat("webp")
                            .toOutputStream(os);
                }

                fileStorageService.storeFile(
                        new ByteArrayInputStream(os.toByteArray()), filename, "image/webp");

            } catch (IllegalArgumentException e) {
                // Fallback for non-image formats (rare with Tika check, but safe)
                logger.error("Resizing failed, fallback to PNG: {}", filename);
                os.reset();
                synchronized (ImageService.class) {
                    Thumbnails.of(sourceFile.toFile())
                            .width(targetWidth)
                            .outputFormat("png")
                            .toOutputStream(os);
                }
                fileStorageService.storeFile(
                        new ByteArrayInputStream(os.toByteArray()), filename, "image/png");
            }
        }
    }

    private ImageMetadataDto extractMetadata(Path sourceFile, String mimeType) {
        ImageMetadataDto metadata = new ImageMetadataDto();
        try (ImageInputStream iis = ImageIO.createImageInputStream(sourceFile.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    metadata.setWidth(reader.getWidth(0));
                    metadata.setHeight(reader.getHeight(0));
                    metadata.setMimeType(mimeType);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            logger.error("Metadata extraction failed", e);
        }

        BufferedImage image = null;
        try {
            synchronized (ImageService.class) {
                image = Thumbnails.of(sourceFile.toFile()).size(64, 64).asBufferedImage();
            }
            if (image != null) {
                metadata.setBlurHash(BlurHash.encode(image, 4, 3));
            }
        } catch (Exception e) {
            logger.error("Blurhash generation failed", e);
        } finally {
            if (image != null) {
                image.flush();
            }
        }

        return metadata;
    }
}
