package com.treishvaam.financeapi.newshighlight;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsHighlightRepository extends JpaRepository<NewsHighlight, Long> {

  // CHANGED: Replaced fixed "Top 10" with Pageable for infinite scroll
  Page<NewsHighlight> findAllByOrderByPublishedAtDesc(Pageable pageable);

  boolean existsByTitle(String title);
}
