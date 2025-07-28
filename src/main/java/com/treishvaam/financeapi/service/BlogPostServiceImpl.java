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

@Service
public class BlogPostServiceImpl implements BlogPostService {

    private static final Logger logger = LoggerFactory.getLogger(BlogPostServiceImpl.class);

    @Autowired
    private BlogPostRepository blogPostRepository;

    // --- MODIFICATION: Replaced FileStorageService with ImageService ---
    @Autowired
    private ImageService imageService;

    @Override
    public List<BlogPost> findAll() {
        return blogPostRepository.findAllByPublishedTrueOrderByCreatedAtDesc();
    }
    
    @Override
    public List<BlogPost> findAllForAdmin() {
        // --- MODIFICATION: Switched to the more efficient repository method ---
        return blogPostRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public Optional<BlogPost> findById(Long id) {
        return blogPostRepository.findById(id);
    }

    @Override
    public BlogPost save(BlogPost blogPost, MultipartFile thumbnail, MultipartFile coverImage) {
        // --- MODIFICATION: This block now uses the new ImageService correctly ---
        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbnailUrl = imageService.saveImage(thumbnail);
            blogPost.setThumbnailUrl(thumbnailUrl);
        }
        if (coverImage != null && !coverImage.isEmpty()) {
            String coverImageUrl = imageService.saveImage(coverImage);
            blogPost.setCoverImageUrl(coverImageUrl);
        }
        // --- End of ImageService logic ---

        if (blogPost.getScheduledTime() != null && blogPost.getScheduledTime().isAfter(Instant.now())) {
            blogPost.setPublished(false);
        } else {
            blogPost.setPublished(true);
            blogPost.setScheduledTime(null);
        }
        
        return blogPostRepository.save(blogPost);
    }

    @Override
    public void deleteById(Long id) {
        blogPostRepository.deleteById(id);
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void checkAndPublishScheduledPosts() {
        // This scheduled method is preserved from your original file
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