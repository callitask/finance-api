package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.controller.ViewController;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CacheWarmupRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(CacheWarmupRunner.class);

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private ViewController viewController;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Starting cache pre-warming for published blog posts...");
        
        List<BlogPost> posts = blogPostRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);

        int successCount = 0;
        for (BlogPost post : posts) {
            // Check if all parts of the new URL are present
            if (post.getCategory() != null && post.getCategory().getSlug() != null &&
                post.getUserFriendlySlug() != null && !post.getUserFriendlySlug().isEmpty() &&
                post.getUrlArticleId() != null && !post.getUrlArticleId().isEmpty()) {
                
                try {
                    logger.info("Pre-rendering and caching page for URL article ID: {}", post.getUrlArticleId());
                    // Call the updated getPostView method with all required path variables
                    viewController.getPostView(
                        post.getCategory().getSlug(),
                        post.getUserFriendlySlug(),
                        post.getUrlArticleId()
                    );
                    successCount++;
                } catch (Exception e) {
                    logger.error("Failed to pre-render page for URL article ID: {}", post.getUrlArticleId(), e);
                }
            } else {
                logger.warn("Skipping cache warmup for post ID {} due to missing URL components.", post.getId());
            }
        }
        logger.info("Cache pre-warming complete. {} pages successfully cached.", successCount);
    }
}