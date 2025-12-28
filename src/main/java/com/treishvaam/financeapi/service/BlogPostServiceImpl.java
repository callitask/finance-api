package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.config.CachingConfig;
import com.treishvaam.financeapi.config.tenant.TenantContext;
import com.treishvaam.financeapi.dto.BlogPostDto;
import com.treishvaam.financeapi.dto.PostThumbnailDto;
import com.treishvaam.financeapi.messaging.MessagePublisher;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.model.PostThumbnail;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.repository.CategoryRepository;
import com.treishvaam.financeapi.service.ImageService.ImageMetadataDto;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BlogPostServiceImpl implements BlogPostService {

  private static final Logger logger = LoggerFactory.getLogger(BlogPostServiceImpl.class);

  @Autowired private BlogPostRepository blogPostRepository;

  @Autowired private MessagePublisher messagePublisher;

  @Autowired private CategoryRepository categoryRepository;
  @Autowired private ImageService imageService;

  private String generateUniqueId() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[8];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String generateUrlArticleId(BlogPost post) {
    if (post == null || post.getCreatedAt() == null || post.getId() == null) return null;
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("EEEddMMyyyyHHmm", Locale.ENGLISH).withZone(ZoneId.of("UTC"));
    return (formatter.format(post.getCreatedAt()) + post.getId()).toLowerCase();
  }

  @Override
  public String generateUserFriendlySlug(String title) {
    if (title == null) return "";
    return title
        .toLowerCase()
        .replaceAll("\\s+", "-")
        .replaceAll("[^a-z0-9-]", "")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }

  @Override
  public List<BlogPost> findAll() {
    return blogPostRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);
  }

  @Override
  public Page<BlogPost> findAll(Pageable pageable) {
    return blogPostRepository.findAll(pageable);
  }

  @Override
  public Page<BlogPost> findAllPublishedPosts(Pageable pageable) {
    return blogPostRepository.findAllByStatus(PostStatus.PUBLISHED, pageable);
  }

  @Override
  public List<BlogPost> findAllForAdmin() {
    return blogPostRepository.findAllByOrderByCreatedAtDesc();
  }

  @Override
  public Optional<BlogPost> findById(Long id) {
    return blogPostRepository.findById(id);
  }

  @Override
  public Optional<BlogPost> findBySlug(String slug) {
    return blogPostRepository.findBySlug(slug);
  }

  @Override
  public Optional<BlogPost> findByUrlArticleId(String urlArticleId) {
    return blogPostRepository.findByUrlArticleId(urlArticleId);
  }

  @Override
  public List<BlogPost> findDrafts() {
    return blogPostRepository.findAllByStatusOrderByUpdatedAtDesc(PostStatus.DRAFT);
  }

  @Override
  @Transactional
  public BlogPost createDraft(BlogPostDto blogPostDto) {
    BlogPost newPost = new BlogPost();
    newPost.setTitle(
        blogPostDto.getTitle() != null && !blogPostDto.getTitle().isEmpty()
            ? blogPostDto.getTitle()
            : "Untitled Draft");
    newPost.setContent(blogPostDto.getContent() != null ? blogPostDto.getContent() : "");
    newPost.setCustomSnippet(blogPostDto.getCustomSnippet());
    newPost.setMetaDescription(blogPostDto.getMetaDescription());
    newPost.setKeywords(blogPostDto.getKeywords());
    newPost.setStatus(PostStatus.DRAFT);
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    newPost.setAuthor(username);

    String currentTenant = TenantContext.getTenantId();
    newPost.setTenantId(
        currentTenant != null && !currentTenant.isEmpty() ? currentTenant : "treishfin");

    newPost.setSlug(generateUniqueId());
    newPost.setLayoutStyle("DEFAULT");
    newPost.setUserFriendlySlug(generateUserFriendlySlug(newPost.getTitle()));
    return blogPostRepository.save(newPost);
  }

  @Override
  @Transactional
  public BlogPost updateDraft(Long id, BlogPostDto blogPostDto) {
    BlogPost existingPost =
        blogPostRepository
            .findById(id)
            .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
    existingPost.setTitle(blogPostDto.getTitle());
    existingPost.setContent(blogPostDto.getContent());
    existingPost.setCustomSnippet(blogPostDto.getCustomSnippet());
    existingPost.setMetaDescription(blogPostDto.getMetaDescription());
    existingPost.setKeywords(blogPostDto.getKeywords());
    if (existingPost.getSlug() == null || existingPost.getSlug().isEmpty())
      existingPost.setSlug(generateUniqueId());
    existingPost.setUserFriendlySlug(generateUserFriendlySlug(existingPost.getTitle()));
    return blogPostRepository.save(existingPost);
  }

  /**
   * ENTERPRISE PATTERN: I/O OUTSIDE TRANSACTION 1. Perform Network I/O (Image Uploads) first. 2.
   * Call transactional method for DB persistence. 3. Publish async events after transaction
   * commits.
   */
  @Override
  // NOTE: No @Transactional here to prevent DB connection holding during MinIO upload
  public BlogPost save(
      BlogPost blogPost,
      List<MultipartFile> newThumbnails,
      List<PostThumbnailDto> thumbnailDtos,
      MultipartFile coverImage) {

    // --- Step 1: Network I/O (Heavy Lifting) ---
    // Handle Cover Image
    if (coverImage != null && !coverImage.isEmpty()) {
      ImageMetadataDto coverMetadata = imageService.saveImageAndGetMetadata(coverImage);
      if (coverMetadata != null) blogPost.setCoverImageUrl(coverMetadata.getBaseFilename());
    }

    // Handle Thumbnails
    Map<String, MultipartFile> newFilesMap =
        newThumbnails != null
            ? newThumbnails.stream()
                .collect(Collectors.toMap(MultipartFile::getOriginalFilename, Function.identity()))
            : Map.of();

    List<PostThumbnail> finalThumbnails = new ArrayList<>();

    // Process thumbnails list (Upload new ones, link existing ones)
    for (PostThumbnailDto dto : thumbnailDtos) {
      PostThumbnail thumbnail;
      if ("new".equals(dto.getSource())) {
        MultipartFile file = newFilesMap.get(dto.getFileName());
        if (file != null && !file.isEmpty()) {
          // Upload to MinIO (Slow I/O)
          ImageMetadataDto metadata = imageService.saveImageAndGetMetadata(file);
          if (metadata == null) continue;
          thumbnail = new PostThumbnail();
          thumbnail.setImageUrl(metadata.getBaseFilename());
          thumbnail.setWidth(metadata.getWidth());
          thumbnail.setHeight(metadata.getHeight());
          thumbnail.setMimeType(metadata.getMimeType());
          thumbnail.setBlurHash(metadata.getBlurHash());
        } else {
          continue;
        }
      } else {
        // Find existing thumbnail in the current post's list
        thumbnail =
            blogPost.getThumbnails().stream()
                .filter(t -> t.getImageUrl().equals(dto.getUrl()))
                .findFirst()
                .orElse(new PostThumbnail());
        if (thumbnail.getId() == null) thumbnail.setImageUrl(dto.getUrl());
      }
      thumbnail.setBlogPost(blogPost);
      thumbnail.setAltText(dto.getAltText());
      thumbnail.setDisplayOrder(dto.getDisplayOrder());
      finalThumbnails.add(thumbnail);
    }

    // --- Step 2: Persistence (Transactional) ---
    // We pass the prepared data to a transactional method.
    BlogPost savedPost = persistPost(blogPost, finalThumbnails);

    // --- Step 3: Async Messaging (Post-Commit) ---
    if (savedPost.getStatus() == PostStatus.PUBLISHED) {
      try {
        messagePublisher.publishSearchIndexEvent(savedPost.getId(), "INDEX");
        messagePublisher.publishSitemapRegenerateEvent();
      } catch (Exception e) {
        logger.error(
            "Failed to publish async events for post ID: {}. Post was saved successfully.",
            savedPost.getId(),
            e);
      }
    }

    return savedPost;
  }

  // Dedicated Transactional Method for DB Operations only
  @Transactional(propagation = Propagation.REQUIRED)
  @CacheEvict(
      value = CachingConfig.BLOG_POST_CACHE,
      key = "#result.urlArticleId",
      condition = "#result.urlArticleId != null and #result.status.name() == 'PUBLISHED'")
  public BlogPost persistPost(BlogPost blogPost, List<PostThumbnail> processedThumbnails) {
    // Update relationships
    blogPost.getThumbnails().clear();
    blogPost.getThumbnails().addAll(processedThumbnails);

    if (blogPost.getSlug() == null || blogPost.getSlug().isEmpty())
      blogPost.setSlug(generateUniqueId());
    if (blogPost.getUserFriendlySlug() == null || blogPost.getUserFriendlySlug().isEmpty())
      blogPost.setUserFriendlySlug(generateUserFriendlySlug(blogPost.getTitle()));

    if (blogPost.getScheduledTime() != null && blogPost.getScheduledTime().isAfter(Instant.now())) {
      blogPost.setStatus(PostStatus.SCHEDULED);
    } else {
      blogPost.setStatus(PostStatus.PUBLISHED);
      blogPost.setScheduledTime(null);
    }

    BlogPost savedPost = blogPostRepository.save(blogPost);

    // Generate permanent ID if published
    if ((savedPost.getStatus() == PostStatus.PUBLISHED
            || savedPost.getStatus() == PostStatus.SCHEDULED)
        && savedPost.getUrlArticleId() == null) {
      savedPost.setUrlArticleId(generateUrlArticleId(savedPost));
      savedPost = blogPostRepository.save(savedPost);
    }

    return savedPost;
  }

  @Override
  @Transactional
  @CacheEvict(value = CachingConfig.BLOG_POST_CACHE, allEntries = true)
  public void deleteById(Long id) {
    blogPostRepository.deleteById(id);
    try {
      messagePublisher.publishSearchIndexEvent(id, "DELETE");
      messagePublisher.publishSitemapRegenerateEvent();
    } catch (Exception e) {
      logger.error("Failed to publish delete events for post ID: {}", id, e);
    }
  }

  @Override
  @Transactional
  @CacheEvict(value = CachingConfig.BLOG_POST_CACHE, allEntries = true)
  public void deletePostsInBulk(List<Long> postIds) {
    if (postIds != null && !postIds.isEmpty()) {
      blogPostRepository.deleteByIdIn(postIds);
      try {
        for (Long id : postIds) {
          messagePublisher.publishSearchIndexEvent(id, "DELETE");
        }
        messagePublisher.publishSitemapRegenerateEvent();
      } catch (Exception e) {
        logger.error("Failed to publish bulk delete events", e);
      }
    }
  }

  @Override
  @Scheduled(fixedRate = 60000)
  @Transactional
  public void checkAndPublishScheduledPosts() {
    List<BlogPost> postsToPublish =
        blogPostRepository.findByStatusAndScheduledTimeBefore(PostStatus.SCHEDULED, Instant.now());
    for (BlogPost post : postsToPublish) {
      post.setStatus(PostStatus.PUBLISHED);
      post.setScheduledTime(null);
      if (post.getUrlArticleId() == null) {
        post.setUrlArticleId(generateUrlArticleId(post));
      }
      blogPostRepository.save(post);

      try {
        messagePublisher.publishSearchIndexEvent(post.getId(), "INDEX");
        logger.info("Published scheduled post with ID: {}", post.getId());
      } catch (Exception e) {
        logger.error("Failed to publish index event for scheduled post: {}", post.getId(), e);
      }
    }
    if (!postsToPublish.isEmpty()) {
      try {
        messagePublisher.publishSitemapRegenerateEvent();
      } catch (Exception e) {
        logger.error("Failed to publish sitemap regeneration event", e);
      }
    }
  }

  @Override
  public List<BlogPost> findAllByStatus(PostStatus status) {
    return blogPostRepository.findAllByStatusOrderByCreatedAtDesc(status);
  }

  @Override
  @Transactional
  public int backfillSlugs() {
    List<BlogPost> posts = blogPostRepository.findAll();
    int count = 0;
    for (BlogPost post : posts) {
      if (post.getUserFriendlySlug() == null || post.getUserFriendlySlug().isEmpty()) {
        post.setUserFriendlySlug(generateUserFriendlySlug(post.getTitle()));
        blogPostRepository.save(post);
        count++;
      }
    }
    return count;
  }

  @Override
  @Transactional
  public int backfillUrlArticleIds() {
    List<BlogPost> posts = blogPostRepository.findAll();
    int count = 0;
    for (BlogPost post : posts) {
      if ((post.getStatus() == PostStatus.PUBLISHED || post.getStatus() == PostStatus.SCHEDULED)
          && post.getUrlArticleId() == null) {
        post.setUrlArticleId(generateUrlArticleId(post));
        blogPostRepository.save(post);
        count++;
      }
    }
    return count;
  }

  @Override
  @Transactional
  public BlogPost duplicatePost(Long id) {
    BlogPost originalPost =
        blogPostRepository
            .findById(id)
            .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
    BlogPost newPost = new BlogPost();
    newPost.setAuthor(SecurityContextHolder.getContext().getAuthentication().getName());

    String currentTenant = TenantContext.getTenantId();
    newPost.setTenantId(
        currentTenant != null && !currentTenant.isEmpty() ? currentTenant : "treishfin");

    newPost.setTitle("Copy of " + originalPost.getTitle());
    newPost.setContent("");
    newPost.setCustomSnippet("");
    newPost.setMetaDescription("");
    newPost.setKeywords("");
    newPost.setCategory(originalPost.getCategory());
    newPost.setTags(new ArrayList<>());
    newPost.setStatus(PostStatus.DRAFT);
    newPost.setSlug(generateUniqueId());
    newPost.setUserFriendlySlug(generateUserFriendlySlug(newPost.getTitle()));
    newPost.setLayoutStyle(originalPost.getLayoutStyle());
    newPost.setLayoutGroupId(null);
    return blogPostRepository.save(newPost);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BlogPost> findPostForUrl(Long id, String categorySlug, String userFriendlySlug) {
    Optional<BlogPost> postOpt =
        blogPostRepository.findByIdAndUserFriendlySlug(id, userFriendlySlug);
    if (postOpt.isEmpty() || postOpt.get().getCategory() == null) return Optional.empty();
    if (postOpt.get().getCategory().getSlug() != null
        && postOpt.get().getCategory().getSlug().equals(categorySlug)) return postOpt;
    return Optional.empty();
  }

  @Override
  public Category findCategoryByName(String name) {
    return categoryRepository
        .findByName(name)
        .orElseThrow(() -> new RuntimeException("Category not found with name: " + name));
  }

  @Override
  public long countPublishedPosts() {
    return blogPostRepository.countByStatus(PostStatus.PUBLISHED);
  }
}