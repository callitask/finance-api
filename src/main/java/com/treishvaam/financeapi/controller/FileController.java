package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api") // This is the critical change to make the path consistent
public class FileController {

    private final FileStorageService fileStorageService;

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
    public ResponseEntity<Map<String, String>> handleFileUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        String fileName = fileStorageService.storeFile(file);
        return ResponseEntity.ok(Map.of("url", "/uploads/" + fileName));
    }
}