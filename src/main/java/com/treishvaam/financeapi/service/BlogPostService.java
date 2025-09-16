package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.dto.BlogPostDto;
import com.treishvaam.financeapi.dto.PostThumbnailDto;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface BlogPostService {
    List<BlogPost> findAll();

    Page<BlogPost> findAll(Pageable pageable);

    List<BlogPost> findAllForAdmin();

    Page<BlogPost> findAllPublishedPosts(Pageable pageable);

    Optional<BlogPost> findById(Long id);

    Optional<BlogPost> findBySlug(String slug);

    BlogPost save(BlogPost blogPost, List<MultipartFile> newThumbnails, List<PostThumbnailDto> thumbnailDtos);

    void deleteById(Long id);
    
    // --- NEW METHOD FOR FEATURE 2: Bulk Actions ---
    void deletePostsInBulk(List<Long> postIds);

    void checkAndPublishScheduledPosts();

    List<BlogPost> findDrafts();

    BlogPost createDraft(BlogPostDto blogPostDto);

    BlogPost updateDraft(Long id, BlogPostDto blogPostDto);

    List<BlogPost> findAllByStatus(PostStatus status);

    int backfillSlugs();

    BlogPost duplicatePost(Long id);
}