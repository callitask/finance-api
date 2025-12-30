package com.treishvaam.financeapi.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

  private final MinioClient minioClient;

  @Value("${minio.bucket-name}")
  private String bucketName;

  // =================================================================================
  // 1. LEGACY/EXISTING METHODS (Restored to fix Compilation Errors)
  // =================================================================================

  /** Stores a MultipartFile with a generated unique name. RESTORED: Used by FileController */
  public String storeFile(MultipartFile file) {
    String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
    String extension = "";
    if (originalFileName.contains(".")) {
      extension = originalFileName.substring(originalFileName.lastIndexOf("."));
    }
    // Generate unique ID
    String fileName = UUID.randomUUID().toString() + extension;

    try {
      uploadFile(file, fileName);
      return fileName;
    } catch (Exception e) {
      throw new RuntimeException("Could not store file " + fileName + ". Please try again!", e);
    }
  }

  /**
   * Stores a file from an InputStream with explicit content type. RESTORED: Used by ImageService
   * and NewsHighlightService FIX: Returns String (fileName) instead of void to satisfy legacy API
   * contracts.
   */
  public String storeFile(ByteArrayInputStream stream, String fileName, String contentType) {
    try {
      long size = stream.available();
      log.info("Uploading stream: {} to bucket: {}", fileName, bucketName);

      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(fileName).stream(stream, size, -1)
              .contentType(contentType)
              .build());
      return fileName;
    } catch (Exception e) {
      log.error("Error uploading stream to MinIO: {}", e.getMessage());
      throw new RuntimeException("Could not store file " + fileName, e);
    }
  }

  /** Gets the size of a stored file. RESTORED: Used by NewsHighlightService */
  public long getFileSize(String fileName) {
    try {
      return minioClient
          .statObject(StatObjectArgs.builder().bucket(bucketName).object(fileName).build())
          .size();
    } catch (Exception e) {
      log.error("Error getting file size for {}: {}", fileName, e.getMessage());
      return 0;
    }
  }

  // =================================================================================
  // 2. CORE & NEW METHODS (Phase 1 SEO Support)
  // =================================================================================

  /** Standard upload for MultipartFile (used internally by storeFile and others) */
  public String uploadFile(MultipartFile file, String fileName) {
    try {
      log.info("Uploading file: {} to bucket: {}", fileName, bucketName);

      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(fileName).stream(
                  file.getInputStream(), file.getSize(), -1)
              .contentType(file.getContentType())
              .build());

      return fileName;
    } catch (Exception e) {
      log.error("Error uploading file to MinIO: {}", e.getMessage());
      throw new RuntimeException("Could not upload file", e);
    }
  }

  /**
   * NEW: Uploads a generated HTML string as a file for SEO Materialization Sets strict
   * Cache-Control headers for Cloudflare.
   */
  public void uploadHtmlFile(String fileName, InputStream stream, long size) {
    try {
      log.info("Uploading Materialized HTML: {} to bucket: {}", fileName, bucketName);

      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(fileName).stream(stream, size, -1)
              .contentType("text/html")
              // Enterprise Cache-Control: Cache for 1 hour, then revalidate at edge
              .extraHeaders(java.util.Map.of("Cache-Control", "public, max-age=3600"))
              .build());

    } catch (Exception e) {
      log.error("Error uploading HTML to MinIO: {}", e.getMessage());
      throw new RuntimeException("Could not upload HTML file", e);
    }
  }

  public String getPresignedUrl(String objectName) {
    try {
      return minioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(bucketName)
              .object(objectName)
              .expiry(7, TimeUnit.DAYS)
              .build());
    } catch (Exception e) {
      log.error("Error generating presigned URL: {}", e.getMessage());
      return "";
    }
  }

  public void deleteFile(String objectName) {
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
      log.info("Deleted file: {}", objectName);
    } catch (Exception e) {
      log.warn("Error deleting file {}: {}", objectName, e.getMessage());
    }
  }
}
