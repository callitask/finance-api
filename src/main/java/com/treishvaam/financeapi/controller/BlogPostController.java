package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class BlogPostController {

    private final BlogPostRepository blogPostRepository;
    private final Path fileStorageLocation = Paths.get(System.getProperty("user.home"), "uploads").toAbsolutePath().normalize();

    public BlogPostController(BlogPostRepository blogPostRepository) {
        this.blogPostRepository = blogPostRepository;
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @GetMapping
    public List<BlogPost> getAllPosts() {
        return blogPostRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogPost> getPostById(@PathVariable Long id) {
        return blogPostRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<BlogPost> createPost(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("category") String category,
            @RequestParam(value = "featured", defaultValue = "false") boolean featured,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authorName = authentication.getName();

        BlogPost newPost = new BlogPost();
        newPost.setTitle(title);
        newPost.setContent(content);
        newPost.setCategory(category);
        newPost.setCreatedAt(LocalDateTime.now());
        newPost.setUpdatedAt(LocalDateTime.now());
        newPost.setAuthor(authorName);
        newPost.setTenantId(authorName);
        newPost.setFeatured(featured);

        if (coverImage != null && !coverImage.isEmpty()) {
            String coverImageUrl = storeFile(coverImage);
            newPost.setCoverImageUrl(coverImageUrl);
        }

        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbnailUrl = storeFile(thumbnail);
            newPost.setThumbnailUrl(thumbnailUrl);
        }

        BlogPost savedPost = blogPostRepository.save(newPost);
        return new ResponseEntity<>(savedPost, HttpStatus.CREATED);
    }

    /**
     * ✅ FIX: This new method handles updating an existing post.
     */
    @PutMapping("/{id}")
    public ResponseEntity<BlogPost> updatePost(@PathVariable Long id,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("category") String category,
            @RequestParam(value = "featured", defaultValue = "false") boolean featured,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail) {

        return blogPostRepository.findById(id).map(existingPost -> {
            existingPost.setTitle(title);
            existingPost.setContent(content);
            existingPost.setCategory(category);
            existingPost.setFeatured(featured);
            existingPost.setUpdatedAt(LocalDateTime.now());

            if (coverImage != null && !coverImage.isEmpty()) {
                String coverImageUrl = storeFile(coverImage);
                existingPost.setCoverImageUrl(coverImageUrl);
            }

            if (thumbnail != null && !thumbnail.isEmpty()) {
                String thumbnailUrl = storeFile(thumbnail);
                existingPost.setThumbnailUrl(thumbnailUrl);
            }
            
            BlogPost updatedPost = blogPostRepository.save(existingPost);
            return ResponseEntity.ok(updatedPost);

        }).orElse(ResponseEntity.notFound().build());
    }

    private String storeFile(MultipartFile file) {
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        try {
            if (fileName.contains("..")) {
                throw new RuntimeException("Sorry! Filename contains invalid path sequence " + fileName);
            }
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation);
            return "/uploads/" + fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }
}