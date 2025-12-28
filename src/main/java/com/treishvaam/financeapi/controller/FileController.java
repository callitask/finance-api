package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.FileStorageService;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class FileController {

  private static final Logger logger = LoggerFactory.getLogger(FileController.class);
  private final FileStorageService fileStorageService;
  private final Path fileStorageLocation;

  @Autowired
  public FileController(
      FileStorageService fileStorageService,
      @Value("${storage.upload-dir:${java.io.tmpdir}/treishvaam-uploads-default}")
          String uploadDir) {
    this.fileStorageService = fileStorageService;
    this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
  }

  @GetMapping("/logo")
  public ResponseEntity<Resource> serveLogo() {
    try {
      Path filePath = this.fileStorageLocation.resolve("logo.webp").normalize();
      Resource resource = new UrlResource(filePath.toUri());

      if (resource.exists() && resource.isReadable()) {
        // ENTERPRISE OPTIMIZATION: Cache logo for 30 days
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("image/webp"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
            .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic()) 
            .body(resource);
      } else {
        logger.error("Cached logo.webp not found or is not readable at path: {}", filePath);
        return ResponseEntity.notFound().build();
      }
    } catch (MalformedURLException ex) {
      logger.error("Error creating URL for logo.webp", ex);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Universal File Upload Endpoint.
   * Compatible with:
   * 1. Standard Form Uploads
   * 2. SunEditor (Rich Text Editor) - Requires strictly formatted JSON response.
   */
  @PostMapping("/files/upload")
  public ResponseEntity<Map<String, Object>> handleFileUpload(
      @RequestParam("file") MultipartFile file) {
    
    if (file == null || file.isEmpty()) {
      // Return 400, but in JSON format for the editor to display a nice error
      return ResponseEntity.badRequest().body(Map.of("errorMessage", "File is empty"));
    }

    try {
      // 1. Store File (MinIO)
      String baseName = fileStorageService.storeFile(file);

      // 2. Generate URLs
      Map<String, String> imageUrls = new HashMap<>();
      String largeUrl = "/api/v1/uploads/" + baseName + ".webp"; // Default to webp if converted, or handle extension
      // Note: FileStorageService currently returns exact path. If service converts, we assume .webp here for now. 
      // Ideally FileStorageService returns the final filename. 
      // The storeFile method in service returns "/api/uploads/filename".
      
      // Let's use the return value from service which is the authoritative path
      String fileUrl = baseName; // service returns relative path e.g. /api/uploads/123_abc.jpg

      imageUrls.put("large", fileUrl);
      
      // 3. Construct Response for SunEditor
      // Spec: { "result": [ { "url": "...", "name": "...", "size": ... } ] }
      Map<String, Object> fileInfo = new HashMap<>();
      fileInfo.put("url", fileUrl);
      fileInfo.put("name", file.getOriginalFilename());
      fileInfo.put("size", file.getSize());
      fileInfo.put("urls", imageUrls); // Extra metadata for our internal use

      List<Map<String, Object>> resultList = new ArrayList<>();
      resultList.add(fileInfo);

      Map<String, Object> response = new HashMap<>();
      response.put("result", resultList);
      
      // Explicit 200 OK for editor parsers
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("Upload failed", e);
      return ResponseEntity.internalServerError().body(Map.of("errorMessage", "Server upload failed: " + e.getMessage()));
    }
  }
}