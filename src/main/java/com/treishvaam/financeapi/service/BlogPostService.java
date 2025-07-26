package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface BlogPostService {
    /**
     * Finds all PUBLISHED posts for the public-facing blog.
     */
    List<BlogPost> findAll();

    /**
     * --- NEW METHOD ---
     * Finds ALL posts (published and scheduled) for the admin panel.
     */
    List<BlogPost> findAllForAdmin();

    Optional<BlogPost> findById(Long id);

    BlogPost save(BlogPost blogPost, MultipartFile thumbnail, MultipartFile coverImage);

    void deleteById(Long id);

    void checkAndPublishScheduledPosts();
}