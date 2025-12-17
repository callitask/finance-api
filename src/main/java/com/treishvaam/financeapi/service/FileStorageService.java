package com.treishvaam.financeapi.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs; // Import StatObject
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

  private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
  private final MinioClient minioClient;

  @Value("${minio.bucket-name}")
  private String bucketName;

  @Value("${storage.s3.endpoint}")
  private String endpoint;

  public FileStorageService(MinioClient minioClient) {
    this.minioClient = minioClient;
  }

  @PostConstruct
  public void init() {
    try {
      boolean found =
          minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
      if (!found) {
        logger.info("🪣 Bucket '{}' not found. Creating it now...", bucketName);
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      } else {
        logger.info("✅ Connected to MinIO bucket: {}", bucketName);
      }
    } catch (Exception e) {
      logger.error("❌ Critical Storage Error: Could not connect to MinIO. Check config.", e);
    }
  }

  public String storeFile(MultipartFile file) {
    try {
      String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
      InputStream inputStream = file.getInputStream();
      return storeFile(inputStream, fileName, file.getContentType());
    } catch (Exception e) {
      throw new RuntimeException("Could not store file " + file.getOriginalFilename(), e);
    }
  }

  public String storeFile(InputStream inputStream, String fileName, String contentType) {
    try {
      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(fileName).stream(
                  inputStream, -1, 10485760)
              .contentType(contentType)
              .build());

      // Correct Nginx Path
      return "/api/uploads/" + fileName;

    } catch (Exception e) {
      throw new RuntimeException("Failed to store file: " + fileName, e);
    }
  }

  // --- NEW FEATURE: Check file size to detect corruption (0-byte files) ---
  public long getFileSize(String path) {
    try {
      // Extract object name from URL path (remove prefixes)
      String objectName = path.replace("/api/uploads/", "").replace("/uploads/", "");
      return minioClient
          .statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build())
          .size();
    } catch (Exception e) {
      // If file not found or error, return -1
      return -1;
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
      return null;
    }
  }
}
