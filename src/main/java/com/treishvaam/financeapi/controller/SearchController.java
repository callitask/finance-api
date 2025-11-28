package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.dto.BlogPostSuggestionDto;
import com.treishvaam.financeapi.search.PostDocument;
import com.treishvaam.financeapi.search.PostSearchRepository;
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
    private PostSearchRepository postSearchRepository;

    @GetMapping
    public ResponseEntity<List<BlogPostSuggestionDto>> searchPosts(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        
        // --- UPDATED: Use the Smart Search method ---
        // This handles "Enterprise", "enterprise", "Enter", and "Architec" correctly.
        List<PostDocument> results = postSearchRepository.searchByTitle(q);
        
        List<BlogPostSuggestionDto> suggestions = results.stream()
                .map(doc -> new BlogPostSuggestionDto(Long.valueOf(doc.getId()), doc.getTitle(), doc.getSlug()))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(suggestions);
    }
}