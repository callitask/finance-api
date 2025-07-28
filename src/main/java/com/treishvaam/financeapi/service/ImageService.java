package com.treishvaam.financeapi.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ImageService {

    private final Path rootLocation;

    // --- FINAL FIX: Provide a safe default value for the test environment ---
    public ImageService(@Value("${storage.upload-dir:${java.io.tmpdir}/treishvaam-uploads-default}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir);
        try {
            if (!Files.exists(rootLocation)) {
                Files.createDirectories(rootLocation);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location: " + uploadDir, e);
        }
    }

    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            String baseFilename = UUID.randomUUID().toString();
            Path largeFile = this.rootLocation.resolve(baseFilename + ".webp");
            Path mediumFile = this.rootLocation.resolve(baseFilename + "-medium.webp");
            Path smallFile = this.rootLocation.resolve(baseFilename + "-small.webp");

            Thumbnails.of(file.getInputStream()).size(1200, 1200).outputFormat("webp").toFile(largeFile.toFile());
            Thumbnails.of(file.getInputStream()).size(600, 600).outputFormat("webp").toFile(mediumFile.toFile());
            Thumbnails.of(file.getInputStream()).size(300, 300).outputFormat("webp").toFile(smallFile.toFile());

            return baseFilename;
        } catch (IOException e) {
            System.err.println("Failed to save image: " + e.getMessage());
            throw new RuntimeException("Failed to save image", e);
        }
    }
}