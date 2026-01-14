package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.FileStorageService;
import com.treishvaam.financeapi.service.ImageService;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI-CONTEXT: Purpose: Handles file uploads and serving (Streaming).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added Fallback Extension Strategy to
 * serveFile() • Reason: Fix 404s when frontend requests UUID without extension but MinIO has .webp
 */
@RestController
@RequestMapping("/api/v1")
public class FileController {

  private static final Logger logger = LoggerFactory.getLogger(FileController.class);
  private final FileStorageService fileStorageService;
  private final ImageService imageService;
  private final Path fileStorageLocation;

  @Autowired
  public FileController(
      FileStorageService fileStorageService,
      ImageService imageService,
      @Value("${storage.upload-dir:${java.io.tmpdir}/treishvaam-uploads-default}")
          String uploadDir) {
    this.fileStorageService = fileStorageService;
    this.imageService = imageService;
    this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
  }

  @GetMapping("/logo")
  public ResponseEntity<Resource> serveLogo() {
    try {
      Path filePath = this.fileStorageLocation.resolve("logo.webp").normalize();
      Resource resource = new UrlResource(filePath.toUri());

      if (resource.exists() && resource.isReadable()) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("image/webp"))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + resource.getFilename() + "\"")
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
   * NEW: Streaming Endpoint for MinIO Files Serves files directly from Object Storage, bypassing
   * local disk. Includes "Smart Extension Matching" for robustness.
   */
  @GetMapping("/uploads/{filename:.+}")
  public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
    try {
      InputStream stream;
      String actualFilename = filename;

      // 1. Try fetching exact filename
      try {
        stream = fileStorageService.loadFileAsStream(filename);
      } catch (Exception e) {
        // 2. Fallback: Try fetching with .webp extension (common for this architecture)
        try {
          actualFilename = filename + ".webp";
          stream = fileStorageService.loadFileAsStream(actualFilename);
        } catch (Exception ex) {
          // If both fail, it's a true 404
          logger.warn("File not found in MinIO (Exact or WebP fallback): {}", filename);
          return ResponseEntity.notFound().build();
        }
      }

      InputStreamResource resource = new InputStreamResource(stream);

      // 3. Determine Content Type
      MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
      if (actualFilename.endsWith(".webp")) mediaType = MediaType.parseMediaType("image/webp");
      else if (actualFilename.endsWith(".jpg") || actualFilename.endsWith(".jpeg"))
        mediaType = MediaType.IMAGE_JPEG;
      else if (actualFilename.endsWith(".png")) mediaType = MediaType.IMAGE_PNG;

      // 4. Return with Caching
      return ResponseEntity.ok()
          .contentType(mediaType)
          .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
          .body(resource);

    } catch (Exception e) {
      logger.error("Error serving file: {}", filename, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  @PostMapping("/files/upload")
  public ResponseEntity<Map<String, Object>> handleFileUpload(
      @RequestParam("file") MultipartFile file) {

    if (file == null || file.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("errorMessage", "File is empty"));
    }

    try {
      String fileUrl;

      if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
        var metadata = imageService.saveImageAndGetMetadata(file);
        fileUrl = metadata.getBaseFilename() + ".webp";
      } else {
        fileUrl = fileStorageService.storeFile(file);
      }

      Map<String, String> imageUrls = new HashMap<>();
      imageUrls.put("large", fileUrl);

      Map<String, Object> fileInfo = new HashMap<>();
      fileInfo.put("url", fileUrl);
      fileInfo.put("name", file.getOriginalFilename());
      fileInfo.put("size", file.getSize());
      fileInfo.put("urls", imageUrls);

      List<Map<String, Object>> resultList = new ArrayList<>();
      resultList.add(fileInfo);

      Map<String, Object> response = new HashMap<>();
      response.put("result", resultList);

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("Upload failed", e);
      return ResponseEntity.internalServerError()
          .body(Map.of("errorMessage", "Server upload failed: " + e.getMessage()));
    }
  }
}
