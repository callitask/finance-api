package com.treishvaam.financeapi.service;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Interface for Blog Post business logic.
 *
 * <p>Scope: - Defines contracts for post management, draft lifecycle, and persistence
 * orchestration.
 *
 * <p>Critical Dependencies: - Backend: Implemented by BlogPostServiceImpl.
 *
 * <p>Security Constraints: - Enforces service-layer boundaries before database operations.
 *
 * <p>Change Intent: - Added `videoFile` signature capability.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (Phase 3 - Enterprise Video Pipeline): •
 * Updated the `save` method signature to accept `MultipartFile videoFile`. • Why: Allows the
 * service layer to process raw video payloads asynchronously.
 */
import com.treishvaam.financeapi.dto.BlogPostDto;
import com.treishvaam.financeapi.dto.PostThumbnailDto;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.model.PostStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface BlogPostService {
    String generateUserFriendlySlug(String title);

    List<BlogPost> findAll();

    Page<BlogPost> findAll(Pageable pageable);

    Page<BlogPost> findAllPublishedPosts(Pageable pageable);

    List<BlogPost> findAllForAdmin();

    Optional<BlogPost> findById(Long id);

    Optional<BlogPost> findBySlug(String slug);

    Optional<BlogPost> findByUrlArticleId(String urlArticleId);

    List<BlogPost> findDrafts();

    BlogPost createDraft(BlogPostDto blogPostDto);

    BlogPost updateDraft(Long id, BlogPostDto blogPostDto);

    /**
     * Orchestrates the saving of a blog post, handling heavy I/O (Image Uploads & Videos) outside
     * of the database transaction.
     */
    BlogPost save(
            BlogPost blogPost,
            List<MultipartFile> newThumbnails,
            List<PostThumbnailDto> thumbnailDtos,
            MultipartFile coverImage,
            MultipartFile videoFile);

    void deleteById(Long id);

    void deletePostsInBulk(List<Long> postIds);

    void checkAndPublishScheduledPosts();

    List<BlogPost> findAllByStatus(PostStatus status);

    int backfillSlugs();

    int backfillUrlArticleIds();

    BlogPost duplicatePost(Long id);

    Optional<BlogPost> findPostForUrl(Long id, String categorySlug, String userFriendlySlug);

    Category findCategoryByName(String name);

    long countPublishedPosts();
}
