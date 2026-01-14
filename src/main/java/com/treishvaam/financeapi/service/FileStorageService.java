package com.treishvaam.financeapi.service;

import io.minio.GetObjectArgs;
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

/**
 * AI-CONTEXT: Purpose: Manages file operations against MinIO Object Storage.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED: • Added loadFileAsStream() for direct
 * MinIO streaming • Reason: Fix 404 errors by serving files directly from cloud storage instead of
 * local disk
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

  private final MinioClient minioClient;

  @Value("${minio.bucket-name}")
  private String bucketName;

  // =================================================================================
  // 1. LEGACY/EXISTING METHODS
  // =================================================================================

  public String storeFile(MultipartFile file) {
    String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
    String extension = "";
    if (originalFileName.contains(".")) {
      extension = originalFileName.substring(originalFileName.lastIndexOf("."));
    }
    String fileName = UUID.randomUUID().toString() + extension;

    try {
      uploadFile(file, fileName);
      return fileName;
    } catch (Exception e) {
      throw new RuntimeException("Could not store file " + fileName + ". Please try again!", e);
    }
  }

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
  // 2. CORE METHODS
  // =================================================================================

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

  public void uploadHtmlFile(String fileName, InputStream stream, long size) {
    try {
      log.info("Uploading Materialized HTML: {} to bucket: {}", fileName, bucketName);

      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(fileName).stream(stream, size, -1)
              .contentType("text/html")
              .extraHeaders(java.util.Map.of("Cache-Control", "public, max-age=3600"))
              .build());

    } catch (Exception e) {
      log.error("Error uploading HTML to MinIO: {}", e.getMessage());
      throw new RuntimeException("Could not upload HTML file", e);
    }
  }

  /**
   * NEW: Streams a file directly from MinIO. Used by FileController to serve images that are not on
   * local disk.
   */
  public InputStream loadFileAsStream(String fileName) {
    try {
      return minioClient.getObject(
          GetObjectArgs.builder().bucket(bucketName).object(fileName).build());
    } catch (Exception e) {
      log.error("Error streaming file from MinIO: {}", fileName, e);
      throw new RuntimeException("Could not retrieve file " + fileName, e);
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
