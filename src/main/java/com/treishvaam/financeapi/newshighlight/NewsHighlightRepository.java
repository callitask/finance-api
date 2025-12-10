package com.treishvaam.financeapi.newshighlight;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsHighlightRepository extends JpaRepository<NewsHighlight, Long> {

  // CHANGED: Only fetch active (non-archived) news for the widget
  Page<NewsHighlight> findByIsArchivedFalseOrderByPublishedAtDesc(Pageable pageable);

  // Use this for the "Historical Search" feature later
  Page<NewsHighlight> findAllByOrderByPublishedAtDesc(Pageable pageable);

  boolean existsByTitle(String title);

  // CHANGED: "Soft Delete" - Mark as archived instead of deleting
  @Modifying
  @Query(
      "UPDATE NewsHighlight n SET n.isArchived = true WHERE n.publishedAt < :cutoff AND n.isArchived = false")
  int archiveOldNews(@Param("cutoff") LocalDateTime cutoff);
}
