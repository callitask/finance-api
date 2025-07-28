package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.service.BlogPostService;
import com.treishvaam.financeapi.dto.ShareRequest;
import com.treishvaam.financeapi.service.LinkedInService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/posts")
public class BlogPostController {

    @Autowired
    private BlogPostService blogPostService;

    // --- MODIFICATION: Made the LinkedInService dependency optional ---
    @Autowired(required = false)
    private LinkedInService linkedInService;

    /**
     * This is the PUBLIC endpoint. It now only returns published posts.
     */
    @GetMapping
    public ResponseEntity<List<BlogPost>> getAllPosts() {
        return ResponseEntity.ok(blogPostService.findAll());
    }

    /**
     * --- NEW ---
     * This is the ADMIN endpoint. It returns all posts (published and scheduled).
     * It requires the user to have an ADMIN role.
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BlogPost>> getAllPostsForAdmin() {
        return ResponseEntity.ok(blogPostService.findAllForAdmin());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogPost> getPostById(@PathVariable Long id) {
        Optional<BlogPost> post = blogPostService.findById(id);
        return post.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BlogPost> createPost(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("category") String category,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam("featured") boolean featured,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestParam(value = "scheduledTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant scheduledTime) {
        
        BlogPost newPost = new BlogPost();
        newPost.setTitle(title);
        newPost.setContent(content);
        newPost.setCategory(category);
        newPost.setTags(tags);
        newPost.setFeatured(featured);
        newPost.setScheduledTime(scheduledTime);
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        newPost.setAuthor(username);
        newPost.setTenantId(username); 

        BlogPost createdPost = blogPostService.save(newPost, thumbnail, coverImage);
        return ResponseEntity.ok(createdPost);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BlogPost> updatePost(
            @PathVariable Long id,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("category") String category,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam("featured") boolean featured,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestParam(value = "scheduledTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant scheduledTime) {
        
        Optional<BlogPost> existingPostOpt = blogPostService.findById(id);
        if (!existingPostOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        BlogPost existingPost = existingPostOpt.get();
        existingPost.setTitle(title);
        existingPost.setContent(content);
        existingPost.setCategory(category);
        existingPost.setTags(tags);
        existingPost.setFeatured(featured);
        existingPost.setScheduledTime(scheduledTime);
        
        BlogPost updatedPost = blogPostService.save(existingPost, thumbnail, coverImage);
        return ResponseEntity.ok(updatedPost);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        blogPostService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/share")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> sharePost(@PathVariable Long id, @RequestBody ShareRequest shareRequest) {
        // --- MODIFICATION: Check if the LinkedIn service is enabled ---
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