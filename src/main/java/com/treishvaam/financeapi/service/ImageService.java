package com.treishvaam.financeapi.service;

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

    /**
     * FINAL FIX: This version uses Java's standard ImageIO to read the PNG and the
     * TwelveMonkeys plugin to write the WebP file, bypassing Thumbnailator for this task.
     */
    @PostConstruct
    public void processAndCacheLogo() {
        // This static block ensures the WebP ImageWriter is available.
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
                // Read the PNG using standard ImageIO
                BufferedImage image = ImageIO.read(inputStream);
                if (image == null) {
                    throw new IOException("Could not read image file. The file may be corrupt or in an unsupported format.");
                }

                // Write the WebP file using the ImageIO plugin
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

    // The original method for user uploads remains unchanged, as it works correctly.
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            String baseFilename = UUID.randomUUID().toString();
            Path largeFile = this.rootLocation.resolve(baseFilename + ".webp");
            Path mediumFile = this.rootLocation.resolve(baseFilename + "-medium.webp");
            Path smallFile = this.rootLocation.resolve(baseFilename + "-small.webp");
            // --- ADDED ---: Path for the new tiny placeholder image
            Path tinyFile = this.rootLocation.resolve(baseFilename + "-tiny.webp");

            // A more robust solution would read the file into a byte array once
            // to avoid issues with multiple reads from the input stream.
            // However, to maintain the existing pattern, we'll open the stream multiple times.
            try (InputStream is1 = file.getInputStream();
                 InputStream is2 = file.getInputStream();
                 InputStream is3 = file.getInputStream();
                 // --- ADDED ---: Fourth input stream for the tiny image
                 InputStream is4 = file.getInputStream()) {

                Thumbnails.of(is1).size(1200, 1200).outputFormat("webp").toFile(largeFile.toFile());
                Thumbnails.of(is2).size(600, 600).outputFormat("webp").toFile(mediumFile.toFile());
                Thumbnails.of(is3).size(300, 300).outputFormat("webp").toFile(smallFile.toFile());
                
                // --- ADDED ---: Logic to create the tiny 20x20 placeholder
                Thumbnails.of(is4).size(20, 20).outputFormat("webp").toFile(tinyFile.toFile());
            }

            return baseFilename;
        } catch (IOException e) {
            logger.error("Failed to save uploaded image", e);
            throw new RuntimeException("Failed to save uploaded image", e);
        }
    }
}