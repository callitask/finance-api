package com.treishvaam.financeapi.config;

import io.minio.MinioClient;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class MinioConfig {

  @Value("${storage.s3.endpoint}")
  private String endpoint;

  @Value("${storage.s3.access-key}")
  private String accessKey;

  @Value("${storage.s3.secret-key}")
  private String secretKey;

  @Value("${storage.s3.region}")
  private String region;

  // --- 1. EXISTING AWS SDK CLIENT (Kept for safety/legacy compatibility) ---
  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .region(Region.of(region))
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true) // MinIO requires path style access
                .build())
        .build();
  }

  // --- 2. NEW MINIO CLIENT (CRITICAL FIX for FileStorageService) ---
  @Bean
  public MinioClient minioClient() {
    return MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .region(region)
        .build();
  }
}
