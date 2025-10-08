package com.treishvaam.financeapi.service;

// import io.trbl.blurhash.BlurHash; // TEMPORARILY COMMENTED OUT
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public ImageMetadata saveImageAndGetMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot process null or empty file.");
        }
        try {
            byte[] imageBytes = file.getBytes();
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (originalImage == null) {
                throw new IOException("Could not decode image from multipart file. It may be corrupt or unsupported.");
            }

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            String mimeType = file.getContentType();
            // String blurHash = BlurHash.encode(originalImage, 4, 3); // TEMPORARILY COMMENTED OUT
            String blurHash = null; // We'll pass null for now.

            String baseFilename = UUID.randomUUID().toString();
            Path largeFile = this.rootLocation.resolve(baseFilename + ".webp");
            Path mediumFile = this.rootLocation.resolve(baseFilename + "-medium.webp");
            Path smallFile = this.rootLocation.resolve(baseFilename + "-small.webp");
            Path tinyFile = this.rootLocation.resolve(baseFilename + "-tiny.webp");

            Thumbnails.of(originalImage).size(1200, 1200).outputFormat("webp").toFile(largeFile.toFile());
            Thumbnails.of(originalImage).size(600, 600).outputFormat("webp").toFile(mediumFile.toFile());
            Thumbnails.of(originalImage).size(300, 300).outputFormat("webp").toFile(smallFile.toFile());
            Thumbnails.of(originalImage).size(20, 20).outputFormat("webp").toFile(tinyFile.toFile());

            return new ImageMetadata(baseFilename, width, height, mimeType, blurHash);

        } catch (IOException e) {
            logger.error("Failed to save or process uploaded image", e);
            throw new RuntimeException("Failed to save or process uploaded image", e);
        }
    }

    @Deprecated
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return saveImageAndGetMetadata(file).baseFilename();
    }
}