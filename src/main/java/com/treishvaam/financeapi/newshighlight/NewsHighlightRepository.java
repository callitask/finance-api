/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Spring Data JPA repository for NewsHighlight (news_highlights table).
 *
 * <p>Scope: - Query methods for active (is_archived=false) and archived (is_archived=true) news
 * items, plus existence checks by link/title.
 *
 * <p>Critical Dependencies: - Backend: JPA/Hibernate deriving from {@code NewsHighlight.java}
 * property names ({@code isArchived} via getter {@code isArchived()}, {@code publishedAt} via
 * column {@code published_at}).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Created NewsHighlightRepository with
 * active + archived paginated finders. • EDITED (Batch 2B - P2.9, VULN-30): • Added {@code
 * findByIsArchivedTrueOrderByPublishedAtDesc(Pageable pageable)} (mirrors the active finder {@code
 * findByIsArchivedFalseOrderByPublishedAtDesc}). • Referenced NewsHighlight field names verified
 * live: boolean field {@code isArchived} + getter {@code isArchived()}, date field {@code
 * publishedAt} + column {@code published_at} — Spring Data derives both finders cleanly.
 */
package com.treishvaam.financeapi.newshighlight;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsHighlightRepository extends JpaRepository<NewsHighlight, Long> {

    // Fetch only active news for the Frontend Widget (Paginated)
    Page<NewsHighlight> findByIsArchivedFalseOrderByPublishedAtDesc(Pageable pageable);

    // Fetch ALL active news for internal maintenance (List version, no pagination)
    List<NewsHighlight> findByIsArchivedFalseOrderByPublishedAtDesc();

    // Fetch archived news for the Archived News UI (Paginated) — VULN-30
    Page<NewsHighlight> findByIsArchivedTrueOrderByPublishedAtDesc(Pageable pageable);

    boolean existsByLink(String link);

    boolean existsByTitle(String title);

    // Count how many articles are currently visible
    long countByIsArchivedFalse();

    // Find the oldest *active* articles (to be archived)
    @Query("SELECT n FROM NewsHighlight n WHERE n.isArchived = false ORDER BY n.publishedAt ASC")
    List<NewsHighlight> findOldestActive(Pageable pageable);
}
