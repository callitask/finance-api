package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface BlogPostService {
    List<BlogPost> findAll();
    Optional<BlogPost> findById(Long id);
    BlogPost save(BlogPost blogPost, MultipartFile thumbnail, MultipartFile coverImage);
    void deleteById(Long id);
}
