package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BlogPostServiceImpl implements BlogPostService {

    private static final Logger logger = LoggerFactory.getLogger(BlogPostServiceImpl.class);

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * This method now correctly returns only PUBLISHED posts for the public blog.
     */
    @Override
    public List<BlogPost> findAll() {
        return blogPostRepository.findAllByPublishedTrueOrderByCreatedAtDesc();
    }
    
    /**
     * This new method returns ALL posts for the admin dashboard, sorted by creation date.
     */
    @Override
    public List<BlogPost> findAllForAdmin() {
        return blogPostRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<BlogPost> findById(Long id) {
        return blogPostRepository.findById(id);
    }

    /**
     * This method now contains the corrected logic for saving scheduled posts.
     */
    @Override
    public BlogPost save(BlogPost blogPost, MultipartFile thumbnail, MultipartFile coverImage) {
        if (blogPost.getId() == null) {
            blogPost.setCreatedAt(Instant.now());
        }
        blogPost.setUpdatedAt(Instant.now());

        // --- FIX: Logic to handle scheduling ---
        if (blogPost.getScheduledTime() != null && blogPost.getScheduledTime().isAfter(Instant.now())) {
            blogPost.setPublished(false);
        } else {
            blogPost.setPublished(true);
            blogPost.setScheduledTime(null); // Clear schedule time if publishing now
        }
        // --- END FIX ---

        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbnailUrl = fileStorageService.storeFile(thumbnail);
            blogPost.setThumbnailUrl(thumbnailUrl);
        }
        if (coverImage != null && !coverImage.isEmpty()) {
            String coverImageUrl = fileStorageService.storeFile(coverImage);
            blogPost.setCoverImageUrl(coverImageUrl);
        }
        return blogPostRepository.save(blogPost);
    }

    @Override
    public void deleteById(Long id) {
        blogPostRepository.deleteById(id);
    }

    @Override
    @Scheduled(fixedRate = 60000) // Runs every minute
    public void checkAndPublishScheduledPosts() {
        logger.info("Checking for scheduled posts to publish...");
        List<BlogPost> postsToPublish = blogPostRepository.findByPublishedFalseAndScheduledTimeBefore(Instant.now());
        
        if (postsToPublish.isEmpty()) {
            return;
        }

        logger.info("Found {} post(s) to publish.", postsToPublish.size());
        for (BlogPost post : postsToPublish) {
            post.setPublished(true);
            post.setUpdatedAt(Instant.now());
            blogPostRepository.save(post);
            logger.info("Published post with ID: {} and title: {}", post.getId(), post.getTitle());
        }
    }
}