package com.treishvaam.financeapi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.dto.BlogPostDto;
import com.treishvaam.financeapi.dto.PostThumbnailDto;
import com.treishvaam.financeapi.dto.ShareRequest;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.service.BlogPostService;
import com.treishvaam.financeapi.service.LinkedInService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/posts")
public class BlogPostController {

    @Autowired
    private BlogPostService blogPostService;

    @Autowired(required = false)
    private LinkedInService linkedInService;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<BlogPost>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        return ResponseEntity.ok(blogPostService.findAllPublishedPosts(pageable));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<BlogPost>> getAllPostsForAdmin() {
        return ResponseEntity.ok(blogPostService.findAllForAdmin());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogPost> getPostById(@PathVariable Long id) {
        Optional<BlogPost> post = blogPostService.findById(id);
        return post.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/url/{urlArticleId}")
    public ResponseEntity<BlogPost> getPostByUrlArticleId(@PathVariable String urlArticleId) {
        return blogPostService.findByUrlArticleId(urlArticleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{categorySlug}/{userFriendlySlug}/{id}")
    public ResponseEntity<BlogPost> getPostByFullSlug(
            @PathVariable String categorySlug,
            @PathVariable String userFriendlySlug,
            @PathVariable Long id) {
        return blogPostService.findPostForUrl(id, categorySlug, userFriendlySlug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/admin/backfill-slugs")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> backfillSlugs() {
        int count = blogPostService.backfillSlugs();
        return ResponseEntity.ok(Map.of("message", "Successfully updated " + count + " posts with new slugs."));
    }

    @GetMapping("/admin/drafts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<BlogPost>> getDrafts() {
        return ResponseEntity.ok(blogPostService.findDrafts());
    }

    @PostMapping("/draft")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BlogPost> createDraft(@RequestBody BlogPostDto postDto) {
        BlogPost createdPost = blogPostService.createDraft(postDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
    }

    @PutMapping("/draft/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BlogPost> updateDraft(@PathVariable Long id, @RequestBody BlogPostDto postDto) {
        BlogPost updatedDraft = blogPostService.updateDraft(id, postDto);
        return ResponseEntity.ok(updatedDraft);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BlogPost> createPost(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "userFriendlySlug", required = false) String userFriendlySlug,
            @RequestParam(value = "customSnippet", required = false) String customSnippet,
            @RequestParam(value = "metaDescription", required = false) String metaDescription,
            @RequestParam(value = "keywords", required = false) String keywords, // NEW PARAM
            @RequestParam("category") String categoryName,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam("featured") boolean featured,
            @RequestParam(value = "scheduledTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant scheduledTime,
            @RequestParam(value = "newThumbnails", required = false) List<MultipartFile> newThumbnails,
            @RequestParam(value = "thumbnailMetadata", required = false) String thumbnailMetadataJson,
            @RequestParam(value = "thumbnailOrientation", required = false) String thumbnailOrientation,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestParam(value = "coverImageAltText", required = false) String coverImageAltText,
            @RequestParam("layoutStyle") String layoutStyle,
            @RequestParam(value = "layoutGroupId", required = false) String layoutGroupId) throws IOException {
        
        Category category = blogPostService.findCategoryByName(categoryName);
        
        BlogPost newPost = new BlogPost();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        newPost.setAuthor(username);
        newPost.setTenantId(username);
        newPost.setTitle(title);
        newPost.setContent(content);
        newPost.setUserFriendlySlug(userFriendlySlug);
        newPost.setCustomSnippet(customSnippet);
        newPost.setMetaDescription(metaDescription);
        newPost.setKeywords(keywords); // NEW
        newPost.setCategory(category);
        newPost.setTags(tags);
        newPost.setFeatured(featured);
        newPost.setScheduledTime(scheduledTime);
        newPost.setThumbnailOrientation(thumbnailOrientation);
        newPost.setCoverImageAltText(coverImageAltText);
        newPost.setLayoutStyle(layoutStyle);
        newPost.setLayoutGroupId(layoutGroupId);
        List<PostThumbnailDto> thumbnailDtos = thumbnailMetadataJson != null ? objectMapper.readValue(thumbnailMetadataJson, new TypeReference<List<PostThumbnailDto>>() {}) : List.of();
        BlogPost savedPost = blogPostService.save(newPost, newThumbnails, thumbnailDtos, coverImage);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPost);
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BlogPost> duplicatePost(@PathVariable Long id) {
        try {
            BlogPost duplicatedPost = blogPostService.duplicatePost(id);
            return ResponseEntity.status(HttpStatus.CREATED).body(duplicatedPost);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BlogPost> updatePost(
            @PathVariable Long id,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "userFriendlySlug", required = false) String userFriendlySlug,
            @RequestParam(value = "customSnippet", required = false) String customSnippet,
            @RequestParam(value = "metaDescription", required = false) String metaDescription,
            @RequestParam(value = "keywords", required = false) String keywords, // NEW PARAM
            @RequestParam("category") String categoryName,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam("featured") boolean featured,
            @RequestParam(value = "scheduledTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant scheduledTime,
            @RequestParam(value = "newThumbnails", required = false) List<MultipartFile> newThumbnails,
            @RequestParam(value = "thumbnailMetadata", required = false) String thumbnailMetadataJson,
            @RequestParam(value = "thumbnailOrientation", required = false) String thumbnailOrientation,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestParam(value = "coverImageAltText", required = false) String coverImageAltText,
            @RequestParam("layoutStyle") String layoutStyle,
            @RequestParam(value = "layoutGroupId", required = false) String layoutGroupId) throws IOException {
        Optional<BlogPost> existingPostOpt = blogPostService.findById(id);
        if (!existingPostOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Category category = blogPostService.findCategoryByName(categoryName);

        BlogPost existingPost = existingPostOpt.get();
        existingPost.setTitle(title);
        existingPost.setContent(content);
        existingPost.setUserFriendlySlug(userFriendlySlug);
        existingPost.setCustomSnippet(customSnippet);
        existingPost.setMetaDescription(metaDescription);
        existingPost.setKeywords(keywords); // NEW
        existingPost.setCategory(category);
        existingPost.setTags(tags);
        existingPost.setFeatured(featured);
        existingPost.setScheduledTime(scheduledTime);
        existingPost.setThumbnailOrientation(thumbnailOrientation);
        existingPost.setCoverImageAltText(coverImageAltText);
        existingPost.setLayoutStyle(layoutStyle);
        existingPost.setLayoutGroupId(layoutGroupId);
        List<PostThumbnailDto> thumbnailDtos = thumbnailMetadataJson != null ? objectMapper.readValue(thumbnailMetadataJson, new TypeReference<List<PostThumbnailDto>>() {}) : List.of();
        BlogPost updatedPost = blogPostService.save(existingPost, newThumbnails, thumbnailDtos, coverImage);
        return ResponseEntity.ok(updatedPost);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        blogPostService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteMultiplePosts(@RequestBody List<Long> postIds) {
        blogPostService.deletePostsInBulk(postIds);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/share")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Mono<ResponseEntity<String>> sharePost(@PathVariable Long id, @RequestBody ShareRequest shareRequest) {
        if (linkedInService == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("LinkedIn integration is currently disabled."));
        }
        Optional<BlogPost> postOpt = blogPostService.findById(id);
        if (!postOpt.isPresent()) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found."));
        }
        BlogPost post = postOpt.get();
        return linkedInService.sharePost(post, shareRequest.getMessage(), shareRequest.getTags())
                .map(response -> ResponseEntity.ok("Successfully shared post to LinkedIn."))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to share post: " + e.getMessage())));
    }
}