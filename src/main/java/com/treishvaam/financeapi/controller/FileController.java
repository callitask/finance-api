package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api") // This is the critical change to make the path consistent
public class FileController {

    private final FileStorageService fileStorageService;

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/uploads/{filename:.+}") // MODIFIED: Changed from /files to /uploads
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Resource file = fileStorageService.loadAsResource(filename);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }

    /**
     * Version endpoint for deployment verification.
     */
    @GetMapping("/version")
    public Map<String, String> getVersion() {
        return Map.of("version", "2.0 - With 50MB Upload Limit");
    }

    @PostMapping("/files/upload")
    public ResponseEntity<Map<String, Object>> handleFileUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("errorMessage", "File is empty");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        String fileName = fileStorageService.storeFile(file);
        String fileDownloadUri = apiBaseUrl + "/uploads/" + fileName;

        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("url", fileDownloadUri);
        fileInfo.put("name", fileName);
        fileInfo.put("size", file.getSize());

        Map<String, Object> response = new HashMap<>();
        response.put("result", Collections.singletonList(fileInfo));

        return ResponseEntity.ok(response);
    }
}