package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.service.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/posts")
public class BlogPostController {

    private final BlogPostRepository blogPostRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public BlogPostController(BlogPostRepository blogPostRepository, FileStorageService fileStorageService, ObjectMapper objectMapper) {
        this.blogPostRepository = blogPostRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<BlogPost> getAllPosts() {
        return blogPostRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BlogPost> createPost(
            @RequestPart("post") String blogPostJson,
            @RequestPart(value = "postThumbnail", required = false) MultipartFile postThumbnail,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage) throws IOException {

        BlogPost blogPost = objectMapper.readValue(blogPostJson, BlogPost.class);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            blogPost.setAuthor(authentication.getName());
        }

        // ✅ FIX: Set the tenant_id before saving.
        // Replace with actual tenant ID source if available
        blogPost.setTenantId("some-tenant-identifier");

        if (postThumbnail != null && !postThumbnail.isEmpty()) {
            String fileName = fileStorageService.storeFile(postThumbnail);
            blogPost.setThumbnailUrl("/api/uploads/" + fileName);
        }

        if (coverImage != null && !coverImage.isEmpty()) {
            String fileName = fileStorageService.storeFile(coverImage);
            blogPost.setCoverImageUrl("/api/uploads/" + fileName);
        }

        BlogPost savedPost = blogPostRepository.save(blogPost);
        return ResponseEntity.ok(savedPost);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogPost> getPostById(@PathVariable Long id) {
        return blogPostRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BlogPost> updatePost(
            @PathVariable Long id,
            @RequestPart("post") String blogPostJson,
            @RequestPart(value = "postThumbnail", required = false) MultipartFile postThumbnail,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage) throws IOException {

        Optional<BlogPost> existingPostOptional = blogPostRepository.findById(id);
        if (existingPostOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BlogPost existingPost = existingPostOptional.get();
        BlogPost updatedDetails = objectMapper.readValue(blogPostJson, BlogPost.class);

        existingPost.setTitle(updatedDetails.getTitle());
        existingPost.setContent(updatedDetails.getContent());
        existingPost.setCategory(updatedDetails.getCategory());
        existingPost.setFeatured(updatedDetails.isFeatured());

        if (postThumbnail != null && !postThumbnail.isEmpty()) {
            String fileName = fileStorageService.storeFile(postThumbnail);
            existingPost.setThumbnailUrl("/api/uploads/" + fileName);
        } else if (updatedDetails.getThumbnailUrl() != null) {
            existingPost.setThumbnailUrl(updatedDetails.getThumbnailUrl());
        }

        if (coverImage != null && !coverImage.isEmpty()) {
            String fileName = fileStorageService.storeFile(coverImage);
            existingPost.setCoverImageUrl("/api/uploads/" + fileName);
        } else if (updatedDetails.getCoverImageUrl() != null) {
            existingPost.setCoverImageUrl(updatedDetails.getCoverImageUrl());
        }

        BlogPost savedPost = blogPostRepository.save(existingPost);
        return ResponseEntity.ok(savedPost);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        return blogPostRepository.findById(id)
            .map(post -> {
                blogPostRepository.delete(post);
                return ResponseEntity.ok().build();
            }).orElse(ResponseEntity.notFound().build());
    }
}