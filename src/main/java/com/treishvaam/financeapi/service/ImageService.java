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
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
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
 * AI-CONTEXT: Purpose: Handles image ingestion, security scanning, and multi-format resizing. *
 * ------------------------------------------------------------------ CRITICAL OPTIMIZATION HISTORY:
 * 1. PAYLOAD REDUCTION (2026-01-13): - Previous Quality: 0.90 (Master), 0.80 (Mobile). Result:
 * 666KB files (Too Heavy). - New Quality: 0.85 (Master), 0.65 (Mobile). Result: ~80KB files
 * (100/100 Score). - Rationale: WebP at 0.65 is visually indistinguishable for news thumbnails but
 * 5x smaller. ------------------------------------------------------------------ Non-Negotiables: -
 * Must use Virtual Threads for parallel resizing. - Must use Tika for security detection. - Must
 * produce WebP format for all variants.
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

  public static class ImageMetadataDto {
    private String baseFilename;
    private Integer width;
    private Integer height;
    private String mimeType;
    private String blurHash;

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

  public ImageMetadataDto saveImageAndGetMetadata(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return null;
    }

    Path tempFile = null;
    try {
      // 1. Stream upload to a temporary file (Zero RAM Impact)
      tempFile = Files.createTempFile("upload_" + UUID.randomUUID(), ".tmp");
      file.transferTo(tempFile.toFile());

      // 2. Security Check: Validate MIME type using Apache Tika
      String detectedMime = tika.detect(tempFile.toFile());
      if (!detectedMime.startsWith("image/")) {
        logger.warn("Security Alert: Invalid image file type detected: {}", detectedMime);
        throw new SecurityException("Invalid file type: " + detectedMime);
      }

      // 3. Extract Metadata from Disk
      ImageMetadataDto metadata = extractMetadata(tempFile, detectedMime);

      String baseFilename = UUID.randomUUID().toString();
      metadata.setBaseFilename(baseFilename);

      String masterName = baseFilename + ".webp"; // 1920w (Master)
      String desktopName = baseFilename + "-1200.webp"; // 1200w
      String tabletName = baseFilename + "-800.webp"; // 800w
      String mobileName = baseFilename + "-480.webp"; // 480w

      // 4. Java 21 Virtual Threads for High Concurrency Resizing
      final Path sourcePath = tempFile;

      // AI-NOTE: TUNED COMPRESSION SETTINGS FOR 100/100 PERFORMANCE SCORE
      // Master: 0.90 -> 0.85 (High Res Backup)
      // Desktop: 0.85 -> 0.75 (Balanced)
      // Tablet: 0.80 -> 0.70 (Aggressive)
      // Mobile: 0.80 -> 0.65 (Max Compression for 4G networks)
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var futures =
            new CompletableFuture<?>[] {
              CompletableFuture.runAsync(
                  () -> uploadResizedSafe(sourcePath, 1920, masterName, 0.85), executor),
              CompletableFuture.runAsync(
                  () -> uploadResizedSafe(sourcePath, 1200, desktopName, 0.75), executor),
              CompletableFuture.runAsync(
                  () -> uploadResizedSafe(sourcePath, 800, tabletName, 0.70), executor),
              CompletableFuture.runAsync(
                  () -> uploadResizedSafe(sourcePath, 480, mobileName, 0.65), executor)
            };

        CompletableFuture.allOf(futures).join();
      }

      return metadata;

    } catch (Exception e) {
      logger.error("Failed to save uploaded image", e);
      throw new RuntimeException("Failed to save uploaded image", e);
    } finally {
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException e) {
          logger.warn("Failed to delete temp file: {}", tempFile, e);
        }
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
        Thumbnails.of(sourceFile.toFile())
            .width(targetWidth)
            .outputQuality(quality)
            .outputFormat("webp")
            .toOutputStream(os);

        byte[] resizedBytes = os.toByteArray();
        fileStorageService.storeFile(
            new ByteArrayInputStream(resizedBytes), filename, "image/webp");

      } catch (IllegalArgumentException e) {
        logger.error("WebP not supported. Falling back to PNG for {}", filename);
        os.reset();

        Thumbnails.of(sourceFile.toFile())
            .width(targetWidth)
            .outputFormat("png")
            .toOutputStream(os);
        byte[] pngBytes = os.toByteArray();
        fileStorageService.storeFile(new ByteArrayInputStream(pngBytes), filename, "image/png");
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
      logger.error("Could not read image metadata", e);
    }

    try (InputStream blurStream = Files.newInputStream(sourceFile)) {
      BufferedImage image = ImageIO.read(blurStream);
      if (image != null) {
        String hash = BlurHash.encode(image, 4, 3);
        metadata.setBlurHash(hash);
      }
    } catch (IOException e) {
      logger.error("Could not generate blurhash", e);
    }

    return metadata;
  }
}
