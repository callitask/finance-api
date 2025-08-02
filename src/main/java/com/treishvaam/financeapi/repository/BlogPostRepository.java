package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional; // <-- ADDED THIS IMPORT

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    List<BlogPost> findAllByOrderByCreatedAtDesc();

    List<BlogPost> findAllByStatusOrderByCreatedAtDesc(PostStatus status);
    List<BlogPost> findAllByStatusOrderByUpdatedAtDesc(PostStatus status);
    List<BlogPost> findByStatusAndScheduledTimeBefore(PostStatus status, Instant time);

    // --- NEW METHOD TO FIND A POST BY ITS SLUG ---
    Optional<BlogPost> findBySlug(String slug);
}