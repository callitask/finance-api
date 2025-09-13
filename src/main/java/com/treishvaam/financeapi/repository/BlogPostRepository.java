package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    List<BlogPost> findAllByStatusOrderByCreatedAtDesc(PostStatus status);
    List<BlogPost> findAllByOrderByCreatedAtDesc();
    Optional<BlogPost> findBySlug(String slug);
    List<BlogPost> findAllByStatusOrderByUpdatedAtDesc(PostStatus status);
    List<BlogPost> findByStatusAndScheduledTimeBefore(PostStatus status, Instant now);
    List<BlogPost> findByTitleContainingIgnoreCaseAndStatus(String title, PostStatus status);

    Page<BlogPost> findAllByStatus(PostStatus status, Pageable pageable);

    long countByLayoutGroupId(String layoutGroupId);

    // --- THIS METHOD WAS MISSING AND IS NOW CORRECTLY ADDED ---
    @Transactional
    void deleteByIdIn(List<Long> ids);
}