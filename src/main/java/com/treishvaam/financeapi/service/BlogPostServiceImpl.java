package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.config.CachingConfig;
import com.treishvaam.financeapi.dto.BlogPostDto;
import com.treishvaam.financeapi.dto.PostThumbnailDto;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.model.PostThumbnail;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BlogPostServiceImpl implements BlogPostService {

    private static final Logger logger = LoggerFactory.getLogger(BlogPostServiceImpl.class);

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ImageService imageService;

    private String generateUniqueId() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String generateUserFriendlySlug(String title) {
        if (title == null) return "";
        return title.toLowerCase()
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
    public List<BlogPost> findDrafts() {
        return blogPostRepository.findAllByStatusOrderByUpdatedAtDesc(PostStatus.DRAFT);
    }

    @Override
    @Transactional
    public BlogPost createDraft(BlogPostDto blogPostDto) {
        BlogPost newPost = new BlogPost();
        newPost.setTitle(blogPostDto.getTitle() != null && !blogPostDto.getTitle().isEmpty() ? blogPostDto.getTitle() : "Untitled Draft");
        newPost.setContent(blogPostDto.getContent() != null ? blogPostDto.getContent() : "");
        newPost.setCustomSnippet(blogPostDto.getCustomSnippet());
        newPost.setStatus(PostStatus.DRAFT);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        newPost.setAuthor(username);
        newPost.setTenantId(username);
        newPost.setSlug(generateUniqueId());
        newPost.setLayoutStyle("DEFAULT");
        // Also generate the user-friendly slug on draft creation
        newPost.setUserFriendlySlug(generateUserFriendlySlug(newPost.getTitle()));
        return blogPostRepository.save(newPost);
    }

    @Override
    @Transactional
    public BlogPost updateDraft(Long id, BlogPostDto blogPostDto) {
        BlogPost existingPost = blogPostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        existingPost.setTitle(blogPostDto.getTitle());
        existingPost.setContent(blogPostDto.getContent());
        existingPost.setCustomSnippet(blogPostDto.getCustomSnippet());
        if (existingPost.getSlug() == null || existingPost.getSlug().isEmpty()) {
            existingPost.setSlug(generateUniqueId());
        }
        // Update the user-friendly slug as well
        existingPost.setUserFriendlySlug(generateUserFriendlySlug(existingPost.getTitle()));
        return blogPostRepository.save(existingPost);
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.BLOG_POST_CACHE, key = "#result.slug", condition = "#result.slug != null and #result.status.name() == 'PUBLISHED'")
    public BlogPost save(BlogPost blogPost, List<MultipartFile> newThumbnails, List<PostThumbnailDto> thumbnailDtos, MultipartFile coverImage) {
        if (coverImage != null && !coverImage.isEmpty()) {
            String coverImageName = imageService.saveImage(coverImage);
            blogPost.setCoverImageUrl(coverImageName);
        }

        Map<String, MultipartFile> newFilesMap = newThumbnails != null ?
                newThumbnails.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, Function.identity())) :
                Map.of();
        List<PostThumbnail> finalThumbnails = new ArrayList<>();
        for (PostThumbnailDto dto : thumbnailDtos) {
            PostThumbnail thumbnail;
            if ("new".equals(dto.getSource())) {
                MultipartFile file = newFilesMap.get(dto.getFileName());
                if (file != null && !file.isEmpty()) {
                    String baseFilename = imageService.saveImage(file);
                    thumbnail = new PostThumbnail();
                    thumbnail.setImageUrl(baseFilename);
                } else {
                    continue;
                }
            } else {
                thumbnail = blogPost.getThumbnails().stream()
                        .filter(t -> t.getImageUrl().equals(dto.getUrl()))
                        .findFirst()
                        .orElse(new PostThumbnail());
                 if(thumbnail.getId() == null) {
                     thumbnail.setImageUrl(dto.getUrl());
                 }
            }
            thumbnail.setBlogPost(blogPost);
            thumbnail.setAltText(dto.getAltText());
            thumbnail.setDisplayOrder(dto.getDisplayOrder());
            finalThumbnails.add(thumbnail);
        }
        blogPost.getThumbnails().clear();
        blogPost.getThumbnails().addAll(finalThumbnails);

        if (blogPost.getSlug() == null || blogPost.getSlug().isEmpty()) {
            blogPost.setSlug(generateUniqueId());
        }
        if (blogPost.getUserFriendlySlug() == null || blogPost.getUserFriendlySlug().isEmpty()) {
            blogPost.setUserFriendlySlug(generateUserFriendlySlug(blogPost.getTitle()));
        }

        if (blogPost.getScheduledTime() != null && blogPost.getScheduledTime().isAfter(Instant.now())) {
            blogPost.setStatus(PostStatus.SCHEDULED);
        } else {
            blogPost.setStatus(PostStatus.PUBLISHED);
            blogPost.setScheduledTime(null);
        }
        return blogPostRepository.save(blogPost);
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.BLOG_POST_CACHE, allEntries = true)
    public void deleteById(Long id) {
        blogPostRepository.deleteById(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.BLOG_POST_CACHE, allEntries = true)
    public void deletePostsInBulk(List<Long> postIds) {
        if(postIds != null && !postIds.isEmpty()) {
            blogPostRepository.deleteByIdIn(postIds);
        }
    }

    @Override
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkAndPublishScheduledPosts() {
        List<BlogPost> postsToPublish = blogPostRepository.findByStatusAndScheduledTimeBefore(PostStatus.SCHEDULED, Instant.now());
        for (BlogPost post : postsToPublish) {
            post.setStatus(PostStatus.PUBLISHED);
            post.setScheduledTime(null);
            blogPostRepository.save(post);
            logger.info("Published scheduled post with ID: {}", post.getId());
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
    public BlogPost duplicatePost(Long id) {
        BlogPost originalPost = blogPostRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));

        BlogPost newPost = new BlogPost();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        newPost.setAuthor(username);
        newPost.setTenantId(username);
        newPost.setTitle("Copy of " + originalPost.getTitle());
        newPost.setContent("");
        newPost.setCustomSnippet("");
        newPost.setCategory(originalPost.getCategory());
        newPost.setTags(new ArrayList<>());
        newPost.setStatus(PostStatus.DRAFT);
        newPost.setSlug(generateUniqueId());
        newPost.setUserFriendlySlug(generateUserFriendlySlug(newPost.getTitle()));

        String layoutStyle = originalPost.getLayoutStyle();
        newPost.setLayoutStyle(layoutStyle);

        if (layoutStyle != null && layoutStyle.startsWith("MULTI_COLUMN")) {
            String originalGroupId = originalPost.getLayoutGroupId();
            try {
                int columnLimit = Integer.parseInt(layoutStyle.split("_")[2]);
                long currentGroupSize = blogPostRepository.countByLayoutGroupId(originalGroupId);

                if (currentGroupSize < columnLimit) {
                    newPost.setLayoutGroupId(originalGroupId);
                } else {
                    newPost.setLayoutGroupId(generateUniqueId());
                }
            } catch (Exception e) {
                newPost.setLayoutGroupId(generateUniqueId());
            }
        } else {
             newPost.setLayoutGroupId(null);
        }

        return blogPostRepository.save(newPost);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<BlogPost> findPostForUrl(Long id, String categorySlug, String userFriendlySlug) {
        // Step 1: Find the post by its unique ID and user-friendly slug
        Optional<BlogPost> postOpt = blogPostRepository.findByIdAndUserFriendlySlug(id, userFriendlySlug);
        if (postOpt.isEmpty()) {
            return Optional.empty(); // Post not found or slug doesn't match
        }
        
        BlogPost post = postOpt.get();

        // Step 2: Since category is just a string on BlogPost, we find the Category entity by its name
        Optional<Category> categoryOpt = categoryRepository.findByName(post.getCategory());
        if (categoryOpt.isEmpty()) {
            return Optional.empty(); // The category assigned to the post doesn't exist
        }

        // Step 3: Validate if the found category's slug matches the one from the URL
        Category category = categoryOpt.get();
        if (category.getSlug() != null && category.getSlug().equals(categorySlug)) {
            return Optional.of(post); // Success, all parts of the URL are valid
        }

        return Optional.empty(); // Category slug doesn't match
    }
}