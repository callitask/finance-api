package com.treishvaam.financeapi.service;

import io.trbl.blurhash.BlurHash;
import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageService {

  private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
  private final FileStorageService fileStorageService;
  private final ResourceLoader resourceLoader;

  public ImageService(FileStorageService fileStorageService, ResourceLoader resourceLoader) {
    this.fileStorageService = fileStorageService;
    this.resourceLoader = resourceLoader;
  }

  public static class ImageMetadataDto {
    private String baseFilename;
    private Integer width;
    private Integer height;
    private String mimeType;
    private String blurHash;

    public String getBaseFilename() { return baseFilename; }
    public void setBaseFilename(String baseFilename) { this.baseFilename = baseFilename; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getBlurHash() { return blurHash; }
    public void setBlurHash(String blurHash) { this.blurHash = blurHash; }
  }

  @PostConstruct
  public void init() {
    try {
      logger.info("Scanning for ImageIO plugins (WebP support)...");
      ImageIO.scanForPlugins();
      logger.info("ImageIO plugins registered successfully.");
    } catch (Exception e) {
      logger.error("Failed to scan ImageIO plugins", e);
    }
  }

  // NOTE: This method performs I/O and CPU intensive tasks. 
  // It is designed to be called BEFORE a transaction starts.
  public ImageMetadataDto saveImageAndGetMetadata(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return null;
    }
    try {
      byte[] imageBytes = file.getBytes();
      ImageMetadataDto metadata = extractMetadata(imageBytes);

      String baseFilename = UUID.randomUUID().toString();
      metadata.setBaseFilename(baseFilename);

      String masterName = baseFilename + ".webp"; // 1920w (Master)
      String desktopName = baseFilename + "-1200.webp"; // 1200w
      String tabletName = baseFilename + "-800.webp"; // 800w
      String mobileName = baseFilename + "-480.webp"; // 480w

      // Java 21 Virtual Threads for High Concurrency
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var futures =
            new CompletableFuture<?>[] {
              CompletableFuture.runAsync(
                  () -> uploadResizedSafe(imageBytes, 1920, masterName, 0.90), executor),
              CompletableFuture.runAsync(
                  () -> uploadResizedSafe(imageBytes, 1200, desktopName, 0.85), executor),
              CompletableFuture.runAsync(
                  () -> uploadResizedSafe(imageBytes, 800, tabletName, 0.80), executor),
              CompletableFuture.runAsync(
                  () -> uploadResizedSafe(imageBytes, 480, mobileName, 0.80), executor)
            };

        CompletableFuture.allOf(futures).join();
      }

      return metadata;

    } catch (Exception e) {
      logger.error("Failed to save uploaded image", e);
      throw new RuntimeException("Failed to save uploaded image", e);
    }
  }

  private void uploadResizedSafe(
      byte[] originalBytes, int targetWidth, String filename, double quality) {
    try {
      uploadResized(originalBytes, targetWidth, filename, quality);
    } catch (IOException e) {
      throw new RuntimeException("Async upload failed for " + filename, e);
    }
  }

  private void uploadResized(byte[] originalBytes, int targetWidth, String filename, double quality)
      throws IOException {
    try (ByteArrayInputStream is = new ByteArrayInputStream(originalBytes);
        ByteArrayOutputStream os = new ByteArrayOutputStream()) {

      try {
        Thumbnails.of(is)
            .width(targetWidth)
            .outputQuality(quality)
            .outputFormat("webp")
            .toOutputStream(os);

        byte[] resizedBytes = os.toByteArray();
        fileStorageService.storeFile(
            new ByteArrayInputStream(resizedBytes), filename, "image/webp");

      } catch (IllegalArgumentException e) {
        // Fallback to PNG
        logger.error("WebP not supported. Falling back to PNG for {}", filename);
        is.reset();
        os.reset();

        Thumbnails.of(is).width(targetWidth).outputFormat("png").toOutputStream(os);
        byte[] pngBytes = os.toByteArray();
        fileStorageService.storeFile(new ByteArrayInputStream(pngBytes), filename, "image/png");
      }
    }
  }

  private ImageMetadataDto extractMetadata(byte[] imageBytes) {
    ImageMetadataDto metadata = new ImageMetadataDto();
    try (ByteArrayInputStream iisBytes = new ByteArrayInputStream(imageBytes)) {

      try (ImageInputStream iis = ImageIO.createImageInputStream(iisBytes)) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (readers.hasNext()) {
          ImageReader reader = readers.next();
          try {
            reader.setInput(iis);
            metadata.setWidth(reader.getWidth(0));
            metadata.setHeight(reader.getHeight(0));
            metadata.setMimeType("image/" + reader.getFormatName().toLowerCase());
          } finally {
            reader.dispose();
          }
        }
      } catch (IOException e) {
        logger.error("Could not read image metadata", e);
      }

      iisBytes.reset();

      try (InputStream blurStream = new ByteArrayInputStream(imageBytes)) {
        BufferedImage image = ImageIO.read(blurStream);
        if (image != null) {
          String hash = BlurHash.encode(image, 4, 3);
          metadata.setBlurHash(hash);
        }
      } catch (IOException e) {
        logger.error("Could not generate blurhash", e);
      }

    } catch (Exception e) {
      logger.error("Failed to extract image metadata", e);
    }
    return metadata;
  }
}