package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// --- FIX: Wrapped the method in a proper RestController class definition ---
@RestController
public class FileController {

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * MODIFIED: This endpoint now processes uploaded images into multiple .webp sizes
     * and returns URLs for each version.
     */
    @PostMapping("/api/files/upload") // Endpoint moved under /api for consistency
    public ResponseEntity<Map<String, Object>> handleFileUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("errorMessage", "File is empty");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // The storeFile method now returns a base name (e.g., "uuid_original-name")
        String baseName = fileStorageService.storeFile(file);

        // This ensures the generated URL matches the actual endpoint for serving files.
        Map<String, String> imageUrls = new HashMap<>();
        // Assuming your files are served from an 'uploads' directory accessible via this path
        imageUrls.put("large", "/uploads/" + baseName + ".webp");
        imageUrls.put("medium", "/uploads/" + baseName + "-medium.webp");
        imageUrls.put("small", "/uploads/" + baseName + "-small.webp");

        // Prepare the response payload for SunEditor compatibility
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("url", imageUrls.get("large")); // SunEditor often expects a single 'url' field
        fileInfo.put("urls", imageUrls);
        fileInfo.put("name", baseName);
        fileInfo.put("size", file.getSize());

        Map<String, Object> response = new HashMap<>();
        response.put("result", Collections.singletonList(fileInfo));

        return ResponseEntity.ok(response);
    }
}