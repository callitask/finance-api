package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.service.FileStorageService; // Import FileStorageService
import com.fasterxml.jackson.databind.ObjectMapper; // Import ObjectMapper for JSON deserialization
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/posts")
public class BlogPostController {

    private final BlogPostRepository blogPostRepository;
    private final FileStorageService fileStorageService; // Inject FileStorageService
    private final ObjectMapper objectMapper; // Inject ObjectMapper

    // Modified constructor to include FileStorageService and ObjectMapper
    public BlogPostController(BlogPostRepository blogPostRepository, FileStorageService fileStorageService, ObjectMapper objectMapper) {
        this.blogPostRepository = blogPostRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<BlogPost> getAllPosts() {
        return blogPostRepository.findAll();
    }

    // MODIFIED: To handle multipart/form-data for creating posts
    @PostMapping
    public ResponseEntity<BlogPost> createPost(
            @RequestPart("post") String blogPostJson, // Blog post data as JSON string
            @RequestPart(value = "postThumbnail", required = false) MultipartFile postThumbnail, // Optional thumbnail file
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage) throws IOException { // Optional cover image file

        BlogPost blogPost = objectMapper.readValue(blogPostJson, BlogPost.class); // Deserialize JSON string to BlogPost object

        // Handle post thumbnail upload
        if (postThumbnail != null && !postThumbnail.isEmpty()) {
            String fileName = fileStorageService.storeFile(postThumbnail);
            blogPost.setImageUrl("/api/uploads/" + fileName); // Store the path relative to your API endpoint
        }
        // If you have a separate field for cover image, you'd handle it similarly
        // For now, assuming imageUrl handles both or you'll decide which one to prioritize
        else if (coverImage != null && !coverImage.isEmpty()) {
             String fileName = fileStorageService.storeFile(coverImage);
             blogPost.setImageUrl("/api/uploads/" + fileName); // Store the path relative to your API endpoint
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

    // MODIFIED: To handle multipart/form-data for updating posts
    @PutMapping("/{id}")
    public ResponseEntity<BlogPost> updatePost(
            @PathVariable Long id,
            @RequestPart("post") String blogPostJson, // Blog post data as JSON string
            @RequestPart(value = "postThumbnail", required = false) MultipartFile postThumbnail, // Optional thumbnail file
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage) throws IOException { // Optional cover image file

        Optional<BlogPost> existingPostOptional = blogPostRepository.findById(id);
        if (existingPostOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BlogPost existingPost = existingPostOptional.get();
        BlogPost updatedDetails = objectMapper.readValue(blogPostJson, BlogPost.class); // Deserialize JSON string

        existingPost.setTitle(updatedDetails.getTitle());
        existingPost.setContent(updatedDetails.getContent());
        existingPost.setAuthor(updatedDetails.getAuthor());
        existingPost.setCategory(updatedDetails.getCategory());
        existingPost.setFeatured(updatedDetails.isFeatured());

        // Handle post thumbnail update
        if (postThumbnail != null && !postThumbnail.isEmpty()) {
            String fileName = fileStorageService.storeFile(postThumbnail);
            existingPost.setImageUrl("/api/uploads/" + fileName); // Update the path
        }
        // If you have a separate field for cover image, handle it similarly
        else if (coverImage != null && !coverImage.isEmpty()) {
            String fileName = fileStorageService.storeFile(coverImage);
            existingPost.setImageUrl("/api/uploads/" + fileName); // Update the path
        }
        // If neither file is provided, but updatedDetails has an imageUrl, use that (e.g., if existing image is kept)
        else if (updatedDetails.getImageUrl() != null) {
            existingPost.setImageUrl(updatedDetails.getImageUrl());
        }


        BlogPost savedPost = blogPostRepository.save(existingPost);
        return ResponseEntity.ok(savedPost);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        return blogPostRepository.findById(id)
            .map(post -> {
                blogPostRepository.delete(post);
                return ResponseEntity.ok().build();
            }).orElse(ResponseEntity.notFound().build());
    }
}
