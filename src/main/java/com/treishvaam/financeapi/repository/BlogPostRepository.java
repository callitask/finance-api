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

    // Keep existing methods
    List<BlogPost> findAllByStatusOrderByCreatedAtDesc(PostStatus status);
    List<BlogPost> findAllByOrderByCreatedAtDesc();
    Optional<BlogPost> findBySlug(String slug);
    List<BlogPost> findAllByStatusOrderByUpdatedAtDesc(PostStatus status);
    List<BlogPost> findByStatusAndScheduledTimeBefore(PostStatus status, Instant now);
    List<BlogPost> findByTitleContainingIgnoreCaseAndStatus(String title, PostStatus status);

    // This is the key method for pagination
    Page<BlogPost> findAllByStatus(PostStatus status, Pageable pageable);

    long countByLayoutGroupId(String layoutGroupId);

    @Transactional
    void deleteByIdIn(List<Long> ids);

    // New method for URL validation
    Optional<BlogPost> findByIdAndUserFriendlySlug(Long id, String userFriendlySlug);

    // NEW METHOD to find by the custom URL ID
    Optional<BlogPost> findByUrlArticleId(String urlArticleId);
}