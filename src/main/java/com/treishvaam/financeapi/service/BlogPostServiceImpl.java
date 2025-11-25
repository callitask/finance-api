package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.config.CachingConfig;
import com.treishvaam.financeapi.config.tenant.TenantContext;
import com.treishvaam.financeapi.dto.BlogPostDto;
import com.treishvaam.financeapi.dto.PostThumbnailDto;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.model.PostThumbnail;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.repository.CategoryRepository;
import com.treishvaam.financeapi.search.PostDocument;
import com.treishvaam.financeapi.search.PostSearchRepository;
import com.treishvaam.financeapi.service.ImageService.ImageMetadataDto;
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

@Service
public class BlogPostServiceImpl implements BlogPostService {

    private static final Logger logger = LoggerFactory.getLogger(BlogPostServiceImpl.class);

    @Autowired
    private BlogPostRepository blogPostRepository;
    
    @Autowired
    private PostSearchRepository postSearchRepository; // NEW: Inject Search Repo
    
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
    
    private String generateUrlArticleId(BlogPost post) {
        if (post == null || post.getCreatedAt() == null || post.getId() == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEddMMyyyyHHmm", Locale.ENGLISH)
                                                     .withZone(ZoneId.of("UTC"));
        String formattedDate = formatter.format(post.getCreatedAt());
        return (formattedDate + post.getId()).toLowerCase();
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
        newPost.setTitle(blogPostDto.getTitle() != null && !blogPostDto.getTitle().isEmpty() ? blogPostDto.getTitle() : "Untitled Draft");
        newPost.setContent(blogPostDto.getContent() != null ? blogPostDto.getContent() : "");
        newPost.setCustomSnippet(blogPostDto.getCustomSnippet());
        newPost.setMetaDescription(blogPostDto.getMetaDescription());
        newPost.setKeywords(blogPostDto.getKeywords());
        newPost.setStatus(PostStatus.DRAFT);
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        newPost.setAuthor(username);
        
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null && !currentTenant.isEmpty()) {
            newPost.setTenantId(currentTenant);
        } else {
            newPost.setTenantId("treishfin");
        }

        newPost.setSlug(generateUniqueId());
        newPost.setLayoutStyle("DEFAULT");
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
        existingPost.setMetaDescription(blogPostDto.getMetaDescription());
        existingPost.setKeywords(blogPostDto.getKeywords());
        if (existingPost.getSlug() == null || existingPost.getSlug().isEmpty()) {
            existingPost.setSlug(generateUniqueId());
        }
        existingPost.setUserFriendlySlug(generateUserFriendlySlug(existingPost.getTitle()));
        return blogPostRepository.save(existingPost);
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.BLOG_POST_CACHE, key = "#result.urlArticleId", condition = "#result.urlArticleId != null and #result.status.name() == 'PUBLISHED'")
    public BlogPost save(BlogPost blogPost, List<MultipartFile> newThumbnails, List<PostThumbnailDto> thumbnailDtos, MultipartFile coverImage) {
        if (coverImage != null && !coverImage.isEmpty()) {
            ImageMetadataDto coverMetadata = imageService.saveImageAndGetMetadata(coverImage);
            if (coverMetadata != null) {
                blogPost.setCoverImageUrl(coverMetadata.getBaseFilename());
            }
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
                    ImageMetadataDto metadata = imageService.saveImageAndGetMetadata(file);
                    if (metadata == null) continue; 

                    thumbnail = new PostThumbnail();
                    thumbnail.setImageUrl(metadata.getBaseFilename());
                    thumbnail.setWidth(metadata.getWidth());
                    thumbnail.setHeight(metadata.getHeight());
                    thumbnail.setMimeType(metadata.getMimeType());
                    thumbnail.setBlurHash(metadata.getBlurHash());
                } else { continue; }
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

        BlogPost savedPost = blogPostRepository.save(blogPost);

        if ((savedPost.getStatus() == PostStatus.PUBLISHED || savedPost.getStatus() == PostStatus.SCHEDULED) && savedPost.getUrlArticleId() == null) {
            savedPost.setUrlArticleId(generateUrlArticleId(savedPost));
            savedPost = blogPostRepository.save(savedPost);
        }
        
        // --- NEW: Index to Elasticsearch ---
        if (savedPost.getStatus() == PostStatus.PUBLISHED) {
            try {
                PostDocument doc = new PostDocument(
                    savedPost.getId().toString(),
                    savedPost.getTitle(),
                    savedPost.getCustomSnippet(), // Index snippet for search previews
                    savedPost.getSlug(),
                    savedPost.getStatus().name()
                );
                postSearchRepository.save(doc);
            } catch (Exception e) {
                logger.error("Failed to index post to Elasticsearch: {}", savedPost.getId(), e);
            }
        }
        
        return savedPost;
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.BLOG_POST_CACHE, allEntries = true)
    public void deleteById(Long id) {
        blogPostRepository.deleteById(id);
        // --- NEW: Delete from Elasticsearch ---
        try {
            postSearchRepository.deleteById(id.toString());
        } catch (Exception e) {
            logger.error("Failed to delete post from Elasticsearch: {}", id, e);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.BLOG_POST_CACHE, allEntries = true)
    public void deletePostsInBulk(List<Long> postIds) {
        if(postIds != null && !postIds.isEmpty()) {
            blogPostRepository.deleteByIdIn(postIds);
            // --- NEW: Bulk Delete from Elasticsearch ---
            try {
                for (Long id : postIds) {
                    postSearchRepository.deleteById(id.toString());
                }
            } catch (Exception e) {
                logger.error("Failed to bulk delete from Elasticsearch", e);
            }
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
            if (post.getUrlArticleId() == null) {
                post.setUrlArticleId(generateUrlArticleId(post));
            }
            blogPostRepository.save(post);
            
            // --- NEW: Index Published Scheduled Post ---
            try {
                PostDocument doc = new PostDocument(
                    post.getId().toString(),
                    post.getTitle(),
                    post.getCustomSnippet(),
                    post.getSlug(),
                    "PUBLISHED"
                );
                postSearchRepository.save(doc);
            } catch (Exception e) {
                logger.error("Failed to index scheduled post to Elasticsearch: {}", post.getId(), e);
            }
            
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
    public int backfillUrlArticleIds() {
        List<BlogPost> posts = blogPostRepository.findAll();
        int count = 0;
        for (BlogPost post : posts) {
            if ((post.getStatus() == PostStatus.PUBLISHED || post.getStatus() == PostStatus.SCHEDULED) && post.getUrlArticleId() == null) {
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
        BlogPost originalPost = blogPostRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));

        BlogPost newPost = new BlogPost();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        newPost.setAuthor(username);
        
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null && !currentTenant.isEmpty()) {
            newPost.setTenantId(currentTenant);
        } else {
            newPost.setTenantId("treishfin");
        }

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
        Optional<BlogPost> postOpt = blogPostRepository.findByIdAndUserFriendlySlug(id, userFriendlySlug);
        if (postOpt.isEmpty() || postOpt.get().getCategory() == null) {
            return Optional.empty();
        }
        BlogPost post = postOpt.get();
        if (post.getCategory().getSlug() != null && post.getCategory().getSlug().equals(categorySlug)) {
            return Optional.of(post);
        }
        return Optional.empty();
    }
    
    @Override
    public Category findCategoryByName(String name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Category not found with name: " + name));
    }

    @Override
    public long countPublishedPosts() {
        return blogPostRepository.countByStatus(PostStatus.PUBLISHED);
    }
}