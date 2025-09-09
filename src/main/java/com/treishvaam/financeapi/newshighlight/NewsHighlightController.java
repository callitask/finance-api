package com.treishvaam.financeapi.newshighlight;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsHighlightController {

    @Autowired
    private NewsHighlightService newsHighlightService;

    @GetMapping("/highlights")
    public ResponseEntity<List<NewsHighlight>> getNewsHighlights() {
        List<NewsHighlight> highlights = newsHighlightService.getNewsHighlights();
        return ResponseEntity.ok(highlights);
    }

    @GetMapping("/archive")
    public ResponseEntity<List<NewsHighlight>> getArchivedNews() {
        List<NewsHighlight> highlights = newsHighlightService.getArchivedNews();
        return ResponseEntity.ok(highlights);
    }

    @PostMapping("/admin/deduplicate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deduplicateNews() {
        String result = newsHighlightService.deduplicateNewsArticles();
        return ResponseEntity.ok(Map.of("message", result));
    }
}