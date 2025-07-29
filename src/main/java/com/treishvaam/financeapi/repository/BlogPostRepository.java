package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    /**
     * --- FIX: Remove the obsolete method that caused the error ---
     * List<BlogPost> findByPublishedFalseAndScheduledTimeBefore(Instant now);
     */

    List<BlogPost> findAllByOrderByCreatedAtDesc();

    // These are the correct, new methods
    List<BlogPost> findAllByStatusOrderByCreatedAtDesc(PostStatus status);
    List<BlogPost> findAllByStatusOrderByUpdatedAtDesc(PostStatus status);
    List<BlogPost> findByStatusAndScheduledTimeBefore(PostStatus status, Instant time);
}