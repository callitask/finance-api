package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.dto.BlogPostSuggestionDto;
import com.treishvaam.financeapi.search.PostDocument;
import com.treishvaam.financeapi.search.PostSearchRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Public autocomplete/search endpoint for published blog posts via Elasticsearch.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - EDITED (2026-11-25 - Batch 1 §1.4 Search DoS
 * Caps): • Bounded `q` length to 200 characters and capped returned suggestions to 50 items.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_RESULTS = 50;

    @Autowired private PostSearchRepository postSearchRepository;

    @GetMapping
    public ResponseEntity<List<BlogPostSuggestionDto>> searchPosts(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        String trimmed = q.trim();
        String boundedQuery =
                trimmed.length() > MAX_QUERY_LENGTH
                        ? trimmed.substring(0, MAX_QUERY_LENGTH)
                        : trimmed;

        List<PostDocument> results = postSearchRepository.searchByTitle(boundedQuery);
        if (results == null || results.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<BlogPostSuggestionDto> suggestions =
                results.stream()
                        .limit(MAX_RESULTS)
                        .map(
                                doc ->
                                        new BlogPostSuggestionDto(
                                                Long.valueOf(doc.getId()),
                                                doc.getTitle(),
                                                doc.getSlug(),
                                                doc.getCategorySlug(),
                                                doc.getUserFriendlySlug(),
                                                doc.getUrlArticleId()))
                        .collect(Collectors.toList());

        return ResponseEntity.ok(suggestions);
    }
}
