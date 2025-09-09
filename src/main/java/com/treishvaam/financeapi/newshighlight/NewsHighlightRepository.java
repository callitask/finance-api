package com.treishvaam.financeapi.newshighlight;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NewsHighlightRepository extends JpaRepository<NewsHighlight, Long> {

    List<NewsHighlight> findTop10ByOrderByPublishedAtDesc();

    List<NewsHighlight> findAllByOrderByPublishedAtDesc(Pageable pageable);

    // New method to check for duplicates by title
    boolean existsByTitle(String title);

    // FIX: Find unique titles instead of links
    @Query("SELECT DISTINCT n.title FROM NewsHighlight n")
    List<String> findDistinctTitles();

    // FIX: Find all articles for a specific title, ordered by date
    List<NewsHighlight> findByTitleOrderByPublishedAtDesc(String title);
}