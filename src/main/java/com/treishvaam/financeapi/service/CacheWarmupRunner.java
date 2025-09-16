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

        for (BlogPost post : posts) {
            if (post.getSlug() != null && !post.getSlug().isEmpty()) {
                try {
                    logger.info("Pre-rendering and caching page for slug: {}", post.getSlug());
                    // The HttpServletRequest is no longer needed
                    viewController.getPostView(post.getSlug());
                } catch (Exception e) {
                    logger.error("Failed to pre-render page for slug: {}", post.getSlug(), e);
                }
            }
        }
        logger.info("Cache pre-warming complete. {} pages cached.", posts.size());
    }
}