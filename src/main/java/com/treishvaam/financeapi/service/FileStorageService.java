package com.treishvaam.financeapi.service;

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

    // --- TEMPORARY DIAGNOSTIC CHANGE START ---
    // Remove @Value and directly assign the path
    public FileStorageService() {
        String temporaryUploadDir = "C:/finance_uploads_test"; // CHOOSE A SIMPLE PATH ON YOUR VM
        this.fileStorageLocation = Paths.get(temporaryUploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }
    // --- TEMPORARY DIAGNOSTIC CHANGE END ---

    public String storeFile(MultipartFile file) {
        // Generate a unique file name to avoid collisions
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

        try {
            // Check if the file's name contains invalid characters
            if (fileName.contains("..")) {
                throw new RuntimeException("Sorry! Filename contains invalid path sequence " + fileName);
            }

            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation);

            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    // --- MODIFICATION START ---
    // Added loadAsResource method to retrieve files
    public Resource loadAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                // If the file does not exist, return null or throw a specific exception
                // For now, returning null as per FileController's check
                return null;
            }
        } catch (MalformedURLException ex) {
            // Handle malformed URL exception
            throw new RuntimeException("File not found " + fileName, ex);
        }
    }
    // --- MODIFICATION END ---
}