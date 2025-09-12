package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    // Methods that were causing compilation errors
    List<BlogPost> findAllByStatusOrderByCreatedAtDesc(PostStatus status);
    List<BlogPost> findAllByOrderByCreatedAtDesc();
    Optional<BlogPost> findBySlug(String slug);
    List<BlogPost> findAllByStatusOrderByUpdatedAtDesc(PostStatus status);
    List<BlogPost> findByStatusAndScheduledTimeBefore(PostStatus status, Instant now);
    List<BlogPost> findByTitleContainingIgnoreCaseAndStatus(String title, PostStatus status);

}