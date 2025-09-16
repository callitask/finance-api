package com.treishvaam.financeapi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.dto.BlogPostDto;
import com.treishvaam.financeapi.dto.PostThumbnailDto;
import com.treishvaam.financeapi.dto.ShareRequest;
import com.treishvaam.financeapi.model.BlogPost;
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

    // ... All other controller methods remain unchanged ...
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BlogPost>> getAllPostsForAdmin() {
        return ResponseEntity.ok(blogPostService.findAllForAdmin());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogPost> getPostById(@PathVariable Long id) {
        return blogPostService.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<BlogPost> getPostBySlug(@PathVariable String slug) {
        return blogPostService.findBySlug(slug).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    
    // ... rest of the methods are identical to what you provided ...
}