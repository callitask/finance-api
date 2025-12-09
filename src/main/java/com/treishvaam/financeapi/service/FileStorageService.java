package com.treishvaam.financeapi.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

  private final MinioClient minioClient;

  @Value("${minio.bucket-name}")
  private String bucketName;

  @Value("${storage.s3.endpoint}") // Ensure this matches your .env/properties
  private String endpoint;

  public FileStorageService(MinioClient minioClient) {
    this.minioClient = minioClient;
  }

  // Existing method for MultipartFile (Controller uploads)
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
      // Ensure bucket exists (Optional, good for safety)
      // boolean found =
      // minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
      // if (!found) { minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      // }

      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(fileName).stream(
                  inputStream, -1, 10485760) // -1 size, 10MB part size
              .contentType(contentType)
              .build());

      // Return the public URL or relative path depending on your Nginx setup
      // Returning relative path assuming Nginx proxies /uploads/ -> MinIO
      return "/uploads/" + fileName;

    } catch (Exception e) {
      throw new RuntimeException("Failed to store file: " + fileName, e);
    }
  }

  // Helper to get presigned URL if needed
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
