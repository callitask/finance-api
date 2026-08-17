package com.treishvaam.financeapi.service;

/**
 * AI-CONTEXT: Purpose: Dedicated service for handling raw video file ingestion and initiating the
 * transcoding pipeline. Scope: Separates file I/O operations and RabbitMQ event publishing from the
 * core blog post logic. IMMUTABLE CHANGE HISTORY: - ADDED (Phase 8): Decoupled raw `.mp4`
 * persistence and RabbitMQ transcode publishing from BlogPostServiceImpl to adhere to Single
 * Responsibility Principle and maintain clean architecture boundaries.
 */
import com.treishvaam.finance.messaging.MessagePublisher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoService {

    private static final Logger logger = LoggerFactory.getLogger(VideoService.class);
    private final MessagePublisher messagePublisher;

    public VideoService(MessagePublisher messagePublisher) {
        this.messagePublisher = messagePublisher;
    }

    public void processVideoUpload(MultipartFile videoFile, Long postId) {
        if (videoFile != null && !videoFile.isEmpty()) {
            try {
                Path rawDir = Paths.get("/app/uploads/raw");
                if (!Files.exists(rawDir)) {
                    Files.createDirectories(rawDir);
                }
                Path filePath = rawDir.resolve(postId + ".mp4");
                Files.copy(
                        videoFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                logger.info(
                        "Raw video payload securely stored at volume path: {}. Queueing transcode event.",
                        filePath);
                messagePublisher.publishVideoTranscodeEvent(postId);
            } catch (Exception e) {
                logger.error(
                        "Failed to save raw video file for post ID: {}. Pipeline aborted.",
                        postId,
                        e);
            }
        }
    }
}
