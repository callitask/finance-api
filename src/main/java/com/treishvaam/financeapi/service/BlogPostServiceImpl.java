package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class BlogPostServiceImpl implements BlogPostService {

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Override
    public List<BlogPost> findAll() {
        return blogPostRepository.findAll();
    }

    @Override
    public Optional<BlogPost> findById(Long id) {
        return blogPostRepository.findById(id);
    }

    @Override
    public BlogPost save(BlogPost blogPost, MultipartFile thumbnail, MultipartFile coverImage) {
        if (blogPost.getCreatedAt() == null) {
            blogPost.setCreatedAt(Instant.now());
        }
        blogPost.setUpdatedAt(Instant.now());

        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbnailUrl = fileStorageService.storeFile(thumbnail);
            blogPost.setThumbnailUrl(thumbnailUrl);
        }
        if (coverImage != null && !coverImage.isEmpty()) {
            String coverImageUrl = fileStorageService.storeFile(coverImage);
            blogPost.setCoverImageUrl(coverImageUrl);
        }
        return blogPostRepository.save(blogPost);
    }

    @Override
    public void deleteById(Long id) {
        blogPostRepository.deleteById(id);
    }
}