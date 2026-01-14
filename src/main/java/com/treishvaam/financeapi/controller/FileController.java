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
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added /uploads/{filename} streaming
 * endpoint • Integrated ImageService for proper WebP conversion on upload • Reason: Fix 404s and
 * enable correct LCP image serving from MinIO
 */
@RestController
@RequestMapping("/api/v1")
public class FileController {

  private static final Logger logger = LoggerFactory.getLogger(FileController.class);
  private final FileStorageService fileStorageService;
  private final ImageService imageService; // Added ImageService
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
   * local disk. Fixes 404 errors for blog images.
   */
  @GetMapping("/uploads/{filename:.+}")
  public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
    try {
      // 1. Stream from MinIO
      InputStream stream = fileStorageService.loadFileAsStream(filename);
      InputStreamResource resource = new InputStreamResource(stream);

      // 2. Determine Content Type
      MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
      if (filename.endsWith(".webp")) mediaType = MediaType.parseMediaType("image/webp");
      else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg"))
        mediaType = MediaType.IMAGE_JPEG;
      else if (filename.endsWith(".png")) mediaType = MediaType.IMAGE_PNG;

      // 3. Return with Caching
      return ResponseEntity.ok()
          .contentType(mediaType)
          .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
          .body(resource);

    } catch (Exception e) {
      logger.warn("File not found in MinIO: {}", filename);
      return ResponseEntity.notFound().build();
    }
  }

  /** Universal File Upload Endpoint. Now integrates ImageService for WebP conversion. */
  @PostMapping("/files/upload")
  public ResponseEntity<Map<String, Object>> handleFileUpload(
      @RequestParam("file") MultipartFile file) {

    if (file == null || file.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("errorMessage", "File is empty"));
    }

    try {
      String fileUrl;

      // Check if it is an image to use ImageService (WebP conversion + Resizing)
      if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
        var metadata = imageService.saveImageAndGetMetadata(file);
        // Return the Master WebP filename
        fileUrl = metadata.getBaseFilename() + ".webp";
      } else {
        // Fallback for non-images (PDFs, etc.)
        fileUrl = fileStorageService.storeFile(file);
      }

      // 2. Construct Response
      Map<String, String> imageUrls = new HashMap<>();
      imageUrls.put("large", fileUrl);

      Map<String, Object> fileInfo = new HashMap<>();
      fileInfo.put("url", fileUrl); // Returns just the filename (e.g., "uuid.webp")
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
