package com.treishvaam.financeapi.newshighlight;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market/news") // FIXED: Added /v1 to match SecurityConfig
public class NewsHighlightController {

  private final NewsHighlightService newsHighlightService;

  public NewsHighlightController(NewsHighlightService newsHighlightService) {
    this.newsHighlightService = newsHighlightService;
  }

  @GetMapping("/highlights")
  public ResponseEntity<List<NewsHighlight>> getNewsHighlights() {
    return ResponseEntity.ok(newsHighlightService.getLatestHighlights());
  }

  @GetMapping("/archived")
  public ResponseEntity<List<NewsHighlight>> getArchivedNews() {
    return ResponseEntity.ok(newsHighlightService.getLatestHighlights());
  }

  @PostMapping("/fetch")
  public ResponseEntity<String> fetchAndSaveNewHighlights(
      @RequestParam(required = false) String region) {
    newsHighlightService.fetchAndStoreNews();
    return ResponseEntity.ok("Enterprise News Fetch Triggered Successfully.");
  }

  @PostMapping("/deduplicate")
  public ResponseEntity<String> deduplicateNewsArticles() {
    return ResponseEntity.ok("Deduplication is now automatic in the ingestion pipeline.");
  }
}
