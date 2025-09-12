package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.dto.BlogPostSuggestionDto;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private BlogPostRepository blogPostRepository;

    @GetMapping
    public ResponseEntity<List<BlogPostSuggestionDto>> searchPosts(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<BlogPost> posts = blogPostRepository.findByTitleContainingIgnoreCaseAndStatus(q, PostStatus.PUBLISHED);
        List<BlogPostSuggestionDto> suggestions = posts.stream()
                .map(post -> new BlogPostSuggestionDto(post.getId(), post.getTitle(), post.getSlug()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(suggestions);
    }
}