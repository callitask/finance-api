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

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

  @Autowired private PostSearchRepository postSearchRepository;

  @GetMapping
  public ResponseEntity<List<BlogPostSuggestionDto>> searchPosts(@RequestParam String q) {
    if (q == null || q.trim().isEmpty()) {
      return ResponseEntity.ok(List.of());
    }

    List<PostDocument> results = postSearchRepository.searchByTitle(q);

    List<BlogPostSuggestionDto> suggestions =
        results.stream()
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
