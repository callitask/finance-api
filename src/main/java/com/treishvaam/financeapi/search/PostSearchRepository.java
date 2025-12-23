package com.treishvaam.financeapi.search;

import java.util.List;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PostSearchRepository extends ElasticsearchRepository<PostDocument, String> {

  // Standard method (Case sensitive wildcard - kept for reference)
  List<PostDocument> findByTitleContaining(String title);

  // --- NEW: Enterprise Search Method ---
  // "match_phrase_prefix": Analyzes the input (lowercases it) and matches partial words.
  // "slop": Allows for a few missing words (optional but good for sentences).
  @Query("{\"match_phrase_prefix\": {\"title\": {\"query\": \"?0\", \"slop\": 2}}}")
  List<PostDocument> searchByTitle(String title);
}
