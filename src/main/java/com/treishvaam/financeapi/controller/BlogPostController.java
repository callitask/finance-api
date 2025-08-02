package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.service.BlogPostService;
import com.treishvaam.financeapi.dto.ShareRequest;
import com.treishvaam.financeapi.service.LinkedInService;
import com.treishvaam.financeapi.dto.BlogPostDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;

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

    @GetMapping
    public ResponseEntity<List<BlogPost>> getAllPosts() {
        return ResponseEntity.ok(blogPostService.findAll());
    }

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

    @GetMapping("/slug/{slug}")
    public ResponseEntity<BlogPost> getPostBySlug(@PathVariable String slug) {
        return blogPostService.findBySlug(slug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    // --- NEW ADMIN ENDPOINT TO BACKFILL SLUGS ---
    @PostMapping("/admin/backfill-slugs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> backfillSlugs() {
        int count = blogPostService.backfillSlugs();
        return ResponseEntity.ok(Map.of("message", "Successfully updated " + count + " posts with new slugs."));
    }

    @GetMapping("/admin/drafts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BlogPost>> getDrafts() {
        return ResponseEntity.ok(blogPostService.findDrafts());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BlogPost> createDraft(@RequestBody BlogPostDto postDto) {
        BlogPost createdPost = blogPostService.createDraft(postDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
    }

    @PutMapping("/draft/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BlogPost> updateDraft(@PathVariable Long id, @RequestBody BlogPostDto postDto) {
        BlogPost updatedDraft = blogPostService.updateDraft(id, postDto);
        return ResponseEntity.ok(updatedDraft);
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