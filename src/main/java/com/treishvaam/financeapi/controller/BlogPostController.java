package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class BlogPostController {

    private final BlogPostRepository blogPostRepository;

    public BlogPostController(BlogPostRepository blogPostRepository) {
        this.blogPostRepository = blogPostRepository;
    }

    @GetMapping
    public List<BlogPost> getAllPosts() {
        return blogPostRepository.findAll();
    }

    @PostMapping
    public BlogPost createPost(@RequestBody BlogPost blogPost) {
        return blogPostRepository.save(blogPost);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogPost> getPostById(@PathVariable Long id) {
        return blogPostRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BlogPost> updatePost(@PathVariable Long id, @RequestBody BlogPost postDetails) {
        return blogPostRepository.findById(id)
            .map(post -> {
                post.setTitle(postDetails.getTitle());
                post.setContent(postDetails.getContent());
                post.setAuthor(postDetails.getAuthor());
                post.setCategory(postDetails.getCategory());
                post.setFeatured(postDetails.isFeatured());
                // This is the new line to handle the featured image URL
                post.setImageUrl(postDetails.getImageUrl()); 
                return ResponseEntity.ok(blogPostRepository.save(post));
            }).orElse(ResponseEntity.notFound().build());
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
