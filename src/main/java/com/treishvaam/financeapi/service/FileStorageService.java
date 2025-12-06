package com.treishvaam.financeapi.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
public class FileStorageService {

  private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

  private final S3Client s3Client;
  private final String bucketName;

  public FileStorageService(S3Client s3Client, @Value("${storage.s3.bucket}") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  @PostConstruct
  public void init() {
    try {
      HeadBucketRequest headBucketRequest = HeadBucketRequest.builder().bucket(bucketName).build();
      s3Client.headBucket(headBucketRequest);
      logger.info("MinIO bucket '{}' exists.", bucketName);
    } catch (NoSuchBucketException e) {
      try {
        CreateBucketRequest bucketRequest =
            CreateBucketRequest.builder().bucket(bucketName).build();
        s3Client.createBucket(bucketRequest);

        // Set bucket policy to public read (Optional, usually handled by Nginx proxy in your setup)
        // But strictly speaking for enterprise, we keep it private and let Nginx proxy it.

        logger.info("MinIO bucket '{}' created successfully.", bucketName);
      } catch (Exception ex) {
        throw new RuntimeException("Could not create MinIO bucket: " + bucketName, ex);
      }
    } catch (Exception e) {
      throw new RuntimeException("Error checking MinIO bucket: " + bucketName, e);
    }
  }

  // Legacy method for direct MultipartFile upload
  public String storeFile(MultipartFile file) {
    try {
      return storeBytes(
          file.getInputStream(), file.getSize(), file.getContentType(), file.getOriginalFilename());
    } catch (IOException e) {
      throw new RuntimeException("Failed to store file " + file.getOriginalFilename(), e);
    }
  }

  // New robust method to store byte streams (used by ImageService after resizing)
  public String storeBytes(
      InputStream inputStream, long size, String contentType, String originalFilename) {
    String extension = "webp"; // Defaulting to webp as per your system standard
    if (originalFilename != null && originalFilename.contains(".")) {
      // If we want to preserve original extension, logic goes here.
      // But your system enforces WebP, so we generate baseName.
    }

    String baseName = UUID.randomUUID().toString();
    String fileName = baseName + "." + extension;

    try {
      PutObjectRequest putOb =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(fileName)
              .contentType(contentType)
              .build();

      s3Client.putObject(putOb, RequestBody.fromInputStream(inputStream, size));
      return baseName; // Returning UUID base
    } catch (Exception e) {
      throw new RuntimeException("Failed to upload file to MinIO", e);
    }
  }

  // Direct upload with explicit filename (for resized versions)
  public void upload(String fileName, byte[] content, String contentType) {
    try {
      PutObjectRequest putOb =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(fileName)
              .contentType(contentType)
              .build();

      s3Client.putObject(putOb, RequestBody.fromBytes(content));
    } catch (Exception e) {
      throw new RuntimeException("Failed to upload " + fileName + " to MinIO", e);
    }
  }

  // Used for serving files if not using Nginx direct proxy
  public Resource loadAsResource(String filename) {
    try {
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().bucket(bucketName).key(filename).build();

      return new InputStreamResource(s3Client.getObject(getObjectRequest));
    } catch (Exception e) {
      throw new RuntimeException("File not found in MinIO: " + filename, e);
    }
  }
}
