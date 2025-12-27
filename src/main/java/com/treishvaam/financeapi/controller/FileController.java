package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.FileStorageService;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1") // Versioned Base Path
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

  // Keep logo at V1 path as well
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

  @PostMapping("/files/upload")
  public ResponseEntity<Map<String, Object>> handleFileUpload(
      @RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("errorMessage", "File is empty"));
    }

    String baseName = fileStorageService.storeFile(file);

    // Update returned URLs to use the V1 path if serving via API
    // NOTE: If serving via Nginx directly, these paths might differ, but for API consistency we use
    // V1.
    // Nginx location block for /api/v1/uploads/ will need to be checked if proxying is done there.
    // Assuming Nginx proxies /api/v1/uploads -> MinIO.

    Map<String, String> imageUrls = new HashMap<>();
    imageUrls.put("large", "/api/v1/uploads/" + baseName + ".webp");
    imageUrls.put("medium", "/api/v1/uploads/" + baseName + "-medium.webp");
    imageUrls.put("small", "/api/v1/uploads/" + baseName + "-small.webp");

    Map<String, Object> fileInfo = new HashMap<>();
    fileInfo.put("url", imageUrls.get("large"));
    fileInfo.put("urls", imageUrls);
    fileInfo.put("name", baseName);
    fileInfo.put("size", file.getSize());

    Map<String, Object> response = Map.of("result", Collections.singletonList(fileInfo));

    return ResponseEntity.ok(response);
  }
}
