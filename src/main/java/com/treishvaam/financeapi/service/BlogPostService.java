package com.treishvaam.financeapi.service;

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
   * Orchestrates the saving of a blog post, handling heavy I/O (Image Uploads)
   * outside of the database transaction.
   */
  BlogPost save(
      BlogPost blogPost,
      List<MultipartFile> newThumbnails,
      List<PostThumbnailDto> thumbnailDtos,
      MultipartFile coverImage);

  // Helper method for persisting the post (Transactional) - Not usually in interface, 
  // but if needed for proxy self-calls it might be. 
  // For now, we only expose the main business methods.

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