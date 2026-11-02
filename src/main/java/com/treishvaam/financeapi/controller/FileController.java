package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.service.FileStorageService;
import com.treishvaam.financeapi.service.ImageService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI-CONTEXT: Purpose: Handles file uploads and serving (Streaming).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added Fallback Extension Strategy to
 * serveFile() • Reason: Fix 404s when frontend requests UUID without extension but MinIO has .webp
 * - EDITED (Phase 12.3 - Multi-Segment Video & HLS Streaming): • Expanded @GetMapping to
 * {"/uploads/{filename:.+}", "/uploads/**"} to support multi-segment paths (/raw/**, /hls/**). •
 * Resolved local filesystem files (/app/uploads) with UrlResource for full HTTP Range 206 streaming
 * before MinIO fallback. • Added MIME types for .m3u8 (application/vnd.apple.mpegurl), .ts
 * (video/mp2t), .mp4 (video/mp4), and .webm (video/webm).
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
     * Streaming Endpoint for Files (Local Disk + MinIO) Supports single-segment and multi-segment
     * paths (e.g. /uploads/raw/71.mp4, /uploads/hls/71/master.m3u8). Local UrlResource allows
     * native Spring HTTP Range (206 Partial Content) video streaming.
     */
    @GetMapping({"/uploads/{filename:.+}", "/uploads/**"})
    public ResponseEntity<Resource> serveFile(
            @PathVariable(required = false) String filename, HttpServletRequest request) {
        try {
            String uri = request.getRequestURI();
            String prefix = "/api/v1/uploads/";
            String subPath;
            if (uri != null && uri.contains(prefix)) {
                subPath = uri.substring(uri.indexOf(prefix) + prefix.length());
            } else if (uri != null && uri.contains("/uploads/")) {
                subPath = uri.substring(uri.indexOf("/uploads/") + "/uploads/".length());
            } else {
                subPath = filename != null ? filename : "";
            }

            subPath = StringUtils.cleanPath(subPath);
            if (subPath.isEmpty() || subPath.contains("..")) {
                return ResponseEntity.badRequest().build();
            }

            // 1. Check local disk first (/app/uploads/<subPath> or uploadDir/<subPath>)
            Path localPath = Paths.get("/app/uploads").resolve(subPath).normalize();
            if (!Files.exists(localPath) || !Files.isRegularFile(localPath)) {
                localPath = this.fileStorageLocation.resolve(subPath).normalize();
            }

            if (Files.exists(localPath) && Files.isRegularFile(localPath)) {
                Resource resource = new UrlResource(localPath.toUri());
                if (resource.isReadable()) {
                    MediaType mediaType = determineMediaType(subPath);
                    CacheControl cacheControl =
                            subPath.endsWith(".m3u8")
                                    ? CacheControl.noCache().mustRevalidate()
                                    : CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic();

                    return ResponseEntity.ok()
                            .contentType(mediaType)
                            .cacheControl(cacheControl)
                            .header(
                                    HttpHeaders.CONTENT_DISPOSITION,
                                    "inline; filename=\"" + resource.getFilename() + "\"")
                            .body(resource);
                }
            }

            // 2. Check MinIO Object Storage
            InputStream stream;
            String actualFilename = subPath;

            try {
                stream = fileStorageService.loadFileAsStream(subPath);
            } catch (Exception e) {
                try {
                    actualFilename = subPath + ".webp";
                    stream = fileStorageService.loadFileAsStream(actualFilename);
                } catch (Exception ex) {
                    logger.warn(
                            "File not found in local disk or MinIO (Exact or WebP fallback): {}",
                            subPath);
                    return ResponseEntity.notFound().build();
                }
            }

            InputStreamResource resource = new InputStreamResource(stream);
            MediaType mediaType = determineMediaType(actualFilename);
            CacheControl cacheControl =
                    actualFilename.endsWith(".m3u8")
                            ? CacheControl.noCache().mustRevalidate()
                            : CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(cacheControl)
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error serving file for URI: {}", request.getRequestURI(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private MediaType determineMediaType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.parseMediaType("image/gif");
        if (lower.endsWith(".svg")) return MediaType.parseMediaType("image/svg+xml");
        if (lower.endsWith(".m3u8"))
            return MediaType.parseMediaType("application/vnd.apple.mpegurl");
        if (lower.endsWith(".ts")) return MediaType.parseMediaType("video/mp2t");
        if (lower.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");
        if (lower.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        return MediaType.APPLICATION_OCTET_STREAM;
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
