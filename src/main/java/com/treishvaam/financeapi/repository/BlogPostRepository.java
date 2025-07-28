package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.BlogPost;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    /**
     * Finds all posts that are not yet published and whose scheduled time is in the past.
     * Used by the scheduler to find posts that are due to be published.
     */
    List<BlogPost> findByPublishedFalseAndScheduledTimeBefore(Instant now);

    /**
     * --- NEW METHOD ---
     * Finds all posts that are marked as published, ordered by the most recent creation date.
     * This will be used for the public-facing blog page to ensure scheduled posts are not shown.
     */
    List<BlogPost> findAllByPublishedTrueOrderByCreatedAtDesc();

    /**
     * --- NEW METHOD ---
     * Finds all posts, ordered by the most recent creation date.
     * This is used for the admin panel to show all posts.
     */
    List<BlogPost> findAllByOrderByCreatedAtDesc();
}