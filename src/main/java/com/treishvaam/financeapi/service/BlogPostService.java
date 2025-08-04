package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.dto.BlogPostDto;
import com.treishvaam.financeapi.dto.PostThumbnailDto;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface BlogPostService {
    List<BlogPost> findAll();

    List<BlogPost> findAllForAdmin();

    Optional<BlogPost> findById(Long id);

    Optional<BlogPost> findBySlug(String slug);

    BlogPost save(BlogPost blogPost, List<MultipartFile> newThumbnails, List<PostThumbnailDto> thumbnailDtos);

    void deleteById(Long id);

    void checkAndPublishScheduledPosts();

    List<BlogPost> findDrafts();

    BlogPost createDraft(BlogPostDto blogPostDto);

    BlogPost updateDraft(Long id, BlogPostDto blogPostDto);
    
    List<BlogPost> findAllByStatus(PostStatus status);

    // --- NEW METHOD FOR BACKFILLING SLUGS ---
    int backfillSlugs();
}