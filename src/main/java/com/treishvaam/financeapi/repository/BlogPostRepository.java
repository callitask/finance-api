package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.BlogPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {
}
