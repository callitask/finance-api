package com.treishvaam.financeapi.newshighlight;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsHighlightRepository extends JpaRepository<NewsHighlight, Long> {

  // Fetch only active news for the Frontend Widget
  Page<NewsHighlight> findByIsArchivedFalseOrderByPublishedAtDesc(Pageable pageable);

  boolean existsByLink(String link);

  boolean existsByTitle(String title);

  // Count how many articles are currently visible
  long countByIsArchivedFalse();

  // Find the oldest *active* articles (to be archived)
  // We sort ASC by publishedAt to find the oldest ones first
  @Query("SELECT n FROM NewsHighlight n WHERE n.isArchived = false ORDER BY n.publishedAt ASC")
  List<NewsHighlight> findOldestActive(Pageable pageable);
}
