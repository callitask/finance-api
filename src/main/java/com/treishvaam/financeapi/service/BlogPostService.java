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
  List<BlogPost> findAll();

  Page<BlogPost> findAll(Pageable pageable);

  List<BlogPost> findAllForAdmin();

  Page<BlogPost> findAllPublishedPosts(Pageable pageable);

  Optional<BlogPost> findById(Long id);

  Optional<BlogPost> findBySlug(String slug);

  BlogPost save(
      BlogPost blogPost,
      List<MultipartFile> newThumbnails,
      List<PostThumbnailDto> thumbnailDtos,
      MultipartFile coverImage);

  void deleteById(Long id);

  void deletePostsInBulk(List<Long> postIds);

  void checkAndPublishScheduledPosts();

  List<BlogPost> findDrafts();

  BlogPost createDraft(BlogPostDto blogPostDto);

  BlogPost updateDraft(Long id, BlogPostDto blogPostDto);

  List<BlogPost> findAllByStatus(PostStatus status);

  int backfillSlugs();

  BlogPost duplicatePost(Long id);

  String generateUserFriendlySlug(String title);

  Optional<BlogPost> findPostForUrl(Long id, String categorySlug, String userFriendlySlug);

  Optional<BlogPost> findByUrlArticleId(String urlArticleId);

  int backfillUrlArticleIds();

  // NEW METHOD TO FIND CATEGORY
  Category findCategoryByName(String name);

  // NEW METHOD for sitemap generation
  long countPublishedPosts();
}
