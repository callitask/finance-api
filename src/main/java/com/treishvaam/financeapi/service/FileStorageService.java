package com.treishvaam.financeapi.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

  private final MinioClient minioClient;

  @Value("${minio.bucket-name}")
  private String bucketName;

  /** Uploads a standard file to MinIO (Images, etc.) */
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
