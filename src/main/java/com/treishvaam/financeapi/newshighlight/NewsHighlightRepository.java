package com.treishvaam.financeapi.newshighlight;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsHighlightRepository extends JpaRepository<NewsHighlight, Long> {

  Page<NewsHighlight> findAllByOrderByPublishedAtDesc(Pageable pageable);

  boolean existsByTitle(String title);

  // CHANGED: Added delete method for cleanup job
  void deleteByPublishedAtBefore(LocalDateTime cutoff);
}
