package com.treishvaam.financeapi.newshighlight;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market/news")
public class NewsHighlightController {

  private final NewsHighlightService newsHighlightService;

  public NewsHighlightController(NewsHighlightService newsHighlightService) {
    this.newsHighlightService = newsHighlightService;
  }

  @GetMapping("/highlights")
  public ResponseEntity<List<NewsHighlight>> getNewsHighlights(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<NewsHighlight> newsPage = newsHighlightService.getHighlights(pageable);
    return ResponseEntity.ok(newsPage.getContent());
  }

  @GetMapping("/archived")
  public ResponseEntity<Page<NewsHighlight>> getArchivedNews(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(newsHighlightService.getHighlights(pageable));
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
