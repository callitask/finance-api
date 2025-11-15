package com.treishvaam.financeapi.service;

import io.trbl.blurhash.BlurHash;
import jakarta.annotation.PostConstruct;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
    private final Path rootLocation;
    private final ResourceLoader resourceLoader;

    public ImageService(
            @Value("${storage.upload-dir:${java.io.tmpdir}/treishvaam-uploads-default}") String uploadDir,
            ResourceLoader resourceLoader) {
        this.rootLocation = Paths.get(uploadDir);
        this.resourceLoader = resourceLoader;
        try {
            if (!Files.exists(rootLocation)) {
                Files.createDirectories(rootLocation);
                logger.info("Created storage directory: {}", rootLocation);
            }
        } catch (IOException e) {
            logger.error("Could not initialize storage location: {}", uploadDir, e);
            throw new RuntimeException("Could not initialize storage location: " + uploadDir, e);
        }
    }

    public static class ImageMetadataDto {
        private String baseFilename;
        private Integer width;
        private Integer height;
        private String mimeType;
        private String blurHash;

        // Getters and Setters
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

    @PostConstruct
    public void processAndCacheLogo() {
        ImageIO.scanForPlugins();
        Path logoWebpPath = this.rootLocation.resolve("logo.webp");

        if (Files.exists(logoWebpPath)) {
            logger.info("logo.webp already exists. Skipping conversion.");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource("classpath:static/images/logo.png");
            if (!resource.exists()) {
                logger.error("FATAL: logo.png not found at classpath:static/images/logo.png");
                throw new RuntimeException("logo.png not found. Please place it in src/main/resources/static/images/");
            }

            try (InputStream inputStream = resource.getInputStream()) {
                BufferedImage image = ImageIO.read(inputStream);
                if (image == null) {
                    throw new IOException("Could not read image file. The file may be corrupt or in an unsupported format.");
                }

                boolean success = ImageIO.write(image, "webp", logoWebpPath.toFile());
                if (!success) {
                    throw new IOException("Failed to convert image to WebP format. No suitable writer found.");
                }
                logger.info("Successfully converted and cached logo.png to logo.webp at {}", logoWebpPath);
            }

        } catch (IOException e) {
            logger.error("Failed to process and cache logo.png", e);
            throw new RuntimeException("Failed to process and cache logo.png", e);
        }
    }

    public ImageMetadataDto saveImageAndGetMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            byte[] imageBytes = file.getBytes();
            ImageMetadataDto metadata = extractMetadata(imageBytes);

            String baseFilename = UUID.randomUUID().toString();
            metadata.setBaseFilename(baseFilename);
            
            Path largeFile = this.rootLocation.resolve(baseFilename + ".webp");
            Path mediumFile = this.rootLocation.resolve(baseFilename + "-medium.webp");
            Path smallFile = this.rootLocation.resolve(baseFilename + "-small.webp");
            Path tinyFile = this.rootLocation.resolve(baseFilename + "-tiny.webp");

            try (InputStream is1 = new ByteArrayInputStream(imageBytes);
                 InputStream is2 = new ByteArrayInputStream(imageBytes);
                 InputStream is3 = new ByteArrayInputStream(imageBytes);
                 InputStream is4 = new ByteArrayInputStream(imageBytes)) {

                // CHANGED: Increased max size from 1200 to 1920. 
                // This ensures images remain high-res enough (>= 1200px) for Google's Article enhancement requirements.
                Thumbnails.of(is1).size(1920, 1920).outputFormat("webp").toFile(largeFile.toFile());
                
                Thumbnails.of(is2).size(600, 600).outputFormat("webp").toFile(mediumFile.toFile());
                Thumbnails.of(is3).size(300, 300).outputFormat("webp").toFile(smallFile.toFile());
                Thumbnails.of(is4).size(20, 20).outputFormat("webp").toFile(tinyFile.toFile());
            }

            return metadata;

        } catch (IOException e) {
            logger.error("Failed to save uploaded image", e);
            throw new RuntimeException("Failed to save uploaded image", e);
        }
    }

    private ImageMetadataDto extractMetadata(byte[] imageBytes) {
        ImageMetadataDto metadata = new ImageMetadataDto();
        try (ByteArrayInputStream iisBytes = new ByteArrayInputStream(imageBytes)) {
            
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
                } else {
                    logger.warn("Could not find ImageReader for uploaded file.");
                }
            } catch (IOException e) {
                logger.error("Could not read image metadata (width, height, mime)", e);
            }

            iisBytes.reset();

            try (InputStream blurStream = new ByteArrayInputStream(imageBytes)) {
                BufferedImage image = ImageIO.read(blurStream);
                if (image != null) {
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