package com.treishvaam.financeapi.service;

import io.trbl.blurhash.BlurHash;
import jakarta.annotation.PostConstruct;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.UUID;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
    private final FileStorageService fileStorageService;
    private final ResourceLoader resourceLoader;

    public ImageService(FileStorageService fileStorageService, ResourceLoader resourceLoader) {
        this.fileStorageService = fileStorageService;
        this.resourceLoader = resourceLoader;
    }

    public static class ImageMetadataDto {
        private String baseFilename;
        private Integer width;
        private Integer height;
        private String mimeType;
        private String blurHash;

        public String getBaseFilename() { return baseFilename; }
        public void setBaseFilename(String baseFilename) { this.baseFilename = baseFilename; }
        public Integer getWidth() { return width; }
        public void setWidth(Integer width) { this.width = width; }
        public Integer getHeight() { return height; }
        public void setHeight(Integer height) { this.height = height; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public String getBlurHash() { return blurHash; }
        public void setBlurHash(String blurHash) { this.blurHash = blurHash; }
    }

    /**
     * Initializes the ImageIO subsystem and registers the TwelveMonkeys WebP plugins.
     * This fixes the "Specified format is not supported: webp" error.
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing ImageIO plugins...");
        ImageIO.scanForPlugins();
        logger.info("ImageIO plugins scanned. WebP support should now be active.");
    }

    public ImageMetadataDto saveImageAndGetMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            // Read into memory once to avoid multiple disk reads or stream issues
            byte[] imageBytes = file.getBytes();
            ImageMetadataDto metadata = extractMetadata(imageBytes);

            String baseFilename = UUID.randomUUID().toString();
            metadata.setBaseFilename(baseFilename);
            
            // Define filenames
            String largeName = baseFilename + ".webp";
            String mediumName = baseFilename + "-medium.webp";
            String smallName = baseFilename + "-small.webp";
            String tinyName = baseFilename + "-tiny.webp";

            // Process and Upload Large (1920px)
            uploadResized(imageBytes, 1920, largeName);
            
            // Process and Upload Medium (600px)
            uploadResized(imageBytes, 600, mediumName);
            
            // Process and Upload Small (300px)
            uploadResized(imageBytes, 300, smallName);
            
            // Process and Upload Tiny (20px - for BlurHash fallback)
            uploadResized(imageBytes, 20, tinyName);

            return metadata;

        } catch (IOException e) {
            logger.error("Failed to save uploaded image", e);
            throw new RuntimeException("Failed to save uploaded image", e);
        }
    }

    /**
     * Resizes and uploads a single image variant.
     * Uses ByteArrayInputStream to ensure thread safety and low memory overhead.
     */
    private void uploadResized(byte[] originalBytes, int targetWidth, String filename) throws IOException {
        try (ByteArrayInputStream is = new ByteArrayInputStream(originalBytes);
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            
            // Thumbnailator automates the resizing and format conversion
            // Requires 'imageio-webp' dependency to work
            Thumbnails.of(is)
                    .size(targetWidth, targetWidth) // Maintains aspect ratio by default
                    .outputFormat("webp")
                    .toOutputStream(os);
            
            byte[] resizedBytes = os.toByteArray();
            fileStorageService.upload(filename, resizedBytes, "image/webp");
        }
    }

    private ImageMetadataDto extractMetadata(byte[] imageBytes) {
        ImageMetadataDto metadata = new ImageMetadataDto();
        try (ByteArrayInputStream iisBytes = new ByteArrayInputStream(imageBytes)) {
            
            // 1. Extract Dimensions & MimeType using ImageReaders
            try (ImageInputStream iis = ImageIO.createImageInputStream(iisBytes)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(iis);
                        metadata.setWidth(reader.getWidth(0));
                        metadata.setHeight(reader.getHeight(0));
                        metadata.setMimeType("image/" + reader.getFormatName().toLowerCase());
                    } finally {
                        reader.dispose();
                    }
                }
            } catch (IOException e) {
                logger.error("Could not read image metadata", e);
            }

            iisBytes.reset();

            // 2. Generate BlurHash
            try (InputStream blurStream = new ByteArrayInputStream(imageBytes)) {
                BufferedImage image = ImageIO.read(blurStream);
                if (image != null) {
                    // 4x3 components offer a good balance of detail vs string length
                    String hash = BlurHash.encode(image, 4, 3);
                    metadata.setBlurHash(hash);
                }
            } catch (IOException e) {
                logger.error("Could not generate blurhash", e);
            }

        } catch (Exception e) {
            logger.error("Failed to extract image metadata", e);
        }
        return metadata;
    }
}