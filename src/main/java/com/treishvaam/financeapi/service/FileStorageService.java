package com.treishvaam.financeapi.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
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

  /**
   * SELF-HEALING: Run on startup to ensure the bucket exists. If the bucket is missing (fresh
   * deploy), it creates it automatically.
   */
  @PostConstruct
  public void init() {
    try {
      boolean found =
          minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
      if (!found) {
        logger.info("🪣 Bucket '{}' not found. Creating it now...", bucketName);
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        logger.info("✅ Bucket created successfully.");
      } else {
        logger.info("✅ Connected to MinIO bucket: {}", bucketName);
      }
    } catch (Exception e) {
      logger.error("❌ Critical Storage Error: Could not connect to MinIO. Check configuration.", e);
      // We don't throw exception here to allow the app to start,
      // but uploads will fail until MinIO is ready.
    }
  }

  // Method for Controller uploads (MultipartFile)
  public String storeFile(MultipartFile file) {
    try {
      String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
      InputStream inputStream = file.getInputStream();
      return storeFile(inputStream, fileName, file.getContentType());
    } catch (Exception e) {
      throw new RuntimeException(
          "Could not store file " + file.getOriginalFilename() + ". Please try again!", e);
    }
  }

  // NEW OVERLOADED METHOD: Accepts raw InputStream (For Internal Image Pipeline)
  public String storeFile(InputStream inputStream, String fileName, String contentType) {
    try {
      // 1. Upload to MinIO
      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(fileName).stream(
                  inputStream, -1, 10485760) // -1 size (unknown), 10MB part size
              .contentType(contentType)
              .build());

      // 2. CRITICAL FIX: Return the correct Nginx proxy path
      // Old: /uploads/ (Resulted in 404 because Nginx didn't know this path)
      // New: /api/uploads/ (Nginx routes this to MinIO)
      return "/api/uploads/" + fileName;

    } catch (Exception e) {
      logger.error("Failed to upload file: {}", fileName, e);
      throw new RuntimeException("Failed to store file: " + fileName, e);
    }
  }

  // Helper to get presigned URL if needed (for private buckets)
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
      logger.error("Could not generate presigned URL for {}", objectName, e);
      return null;
    }
  }
}
