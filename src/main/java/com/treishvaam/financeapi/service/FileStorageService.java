package com.treishvaam.financeapi.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService() {
        String uploadDir = System.getProperty("user.home") + "/uploads";
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored. Path: " + uploadDir, ex);
        }
    }

    public String storeFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.contains("..")) {
            throw new RuntimeException("Sorry! Filename contains invalid path sequence " + originalFilename);
        }

        String extension = "webp";
        String baseName = UUID.randomUUID().toString() + "_" + originalFilename.replaceAll("\\..*$", "");

        try {
            Path largePath = this.fileStorageLocation.resolve(baseName + "." + extension);
            Path mediumPath = this.fileStorageLocation.resolve(baseName + "-medium." + extension);
            Path smallPath = this.fileStorageLocation.resolve(baseName + "-small." + extension);

            // Use Thumbnailator for the entire read, resize, and write operation.
            // This is more robust and correctly utilizes the WebP writer.
            Thumbnails.of(file.getInputStream())
                .size(1200, 1200)
                .outputFormat("webp")
                .toFile(largePath.toFile());

            Thumbnails.of(largePath.toFile())
                .size(600, 600)
                .outputFormat("webp")
                .toFile(mediumPath.toFile());

            Thumbnails.of(largePath.toFile())
                .size(300, 300)
                .outputFormat("webp")
                .toFile(smallPath.toFile());
            
            return baseName;

        } catch (IOException ex) {
            throw new RuntimeException("Could not store and process file " + originalFilename, ex);
        }
    }

    public Resource loadAsResource(String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                return null;
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found " + filename, ex);
        }
    }
}