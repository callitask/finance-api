package com.treishvaam.financeapi.newshighlight;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/market/news")
public class NewsHighlightController {

  private final NewsHighlightService newsHighlightService;

  public NewsHighlightController(NewsHighlightService newsHighlightService) {
    this.newsHighlightService = newsHighlightService;
  }

  // Updated: Maps to getLatestHighlights()
  @GetMapping("/highlights")
  public ResponseEntity<List<NewsHighlight>> getNewsHighlights() {
    return ResponseEntity.ok(newsHighlightService.getLatestHighlights());
  }

  // Updated: Maps to getLatestHighlights() (or separate archive method if you add it later)
  @GetMapping("/archived")
  public ResponseEntity<List<NewsHighlight>> getArchivedNews() {
    // For now, returning latest, or implement getArchivedHighlights in service
    return ResponseEntity.ok(newsHighlightService.getLatestHighlights());
  }

  // Updated: Maps to fetchAndStoreNews()
  @PostMapping("/fetch")
  public ResponseEntity<String> fetchAndSaveNewHighlights(
      @RequestParam(required = false) String region) {
    // 'region' is ignored in the new Enterprise pipeline as it auto-fetches global context
    newsHighlightService.fetchAndStoreNews();
    return ResponseEntity.ok("Enterprise News Fetch Triggered Successfully.");
  }

  // Updated: Deprecated endpoint, mapped to main fetch to maintain API compatibility
  @PostMapping("/deduplicate")
  public ResponseEntity<String> deduplicateNewsArticles() {
    // Deduplication is now built-in to fetchAndStoreNews
    return ResponseEntity.ok("Deduplication is now automatic in the ingestion pipeline.");
  }
}
