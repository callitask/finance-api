package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.dto.BlogPostDto;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class BlogPostServiceImpl implements BlogPostService {

    private static final Logger logger = LoggerFactory.getLogger(BlogPostServiceImpl.class);

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private ImageService imageService;

    @Override
    public List<BlogPost> findAll() {
        return blogPostRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);
    }
    
    @Override
    public List<BlogPost> findAllForAdmin() {
        return blogPostRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public Optional<BlogPost> findById(Long id) {
        return blogPostRepository.findById(id);
    }

    @Override
    public List<BlogPost> findDrafts() {
        return blogPostRepository.findAllByStatusOrderByUpdatedAtDesc(PostStatus.DRAFT);
    }

    @Override
    @Transactional
    public BlogPost createDraft(BlogPostDto blogPostDto) {
        BlogPost newPost = new BlogPost();
        newPost.setTitle(blogPostDto.getTitle() != null && !blogPostDto.getTitle().isEmpty() ? blogPostDto.getTitle() : "Untitled Draft");
        newPost.setContent(blogPostDto.getContent() != null ? blogPostDto.getContent() : "");
        newPost.setStatus(PostStatus.DRAFT);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        newPost.setAuthor(username);
        newPost.setTenantId(username);
        return blogPostRepository.save(newPost);
    }

    @Override
    @Transactional
    public BlogPost updateDraft(Long id, BlogPostDto blogPostDto) {
        BlogPost existingPost = blogPostRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        existingPost.setTitle(blogPostDto.getTitle());
        existingPost.setContent(blogPostDto.getContent());
        return blogPostRepository.save(existingPost);
    }

    @Override
    @Transactional
    public BlogPost save(BlogPost blogPost, MultipartFile thumbnail, MultipartFile coverImage) {
        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbnailUrl = imageService.saveImage(thumbnail);
            blogPost.setThumbnailUrl(thumbnailUrl);
        }
        if (coverImage != null && !coverImage.isEmpty()) {
            String coverImageUrl = imageService.saveImage(coverImage);
            blogPost.setCoverImageUrl(coverImageUrl);
        }
        if (blogPost.getScheduledTime() != null && blogPost.getScheduledTime().isAfter(Instant.now())) {
            blogPost.setStatus(PostStatus.SCHEDULED);
        } else {
            blogPost.setStatus(PostStatus.PUBLISHED);
            blogPost.setScheduledTime(null);
        }
        return blogPostRepository.save(blogPost);
    }

    // --- MODIFICATION START: Implement the missing deleteById method ---
    @Override
    @Transactional
    public void deleteById(Long id) {
        blogPostRepository.deleteById(id);
    }
    // --- MODIFICATION END ---

    @Override
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkAndPublishScheduledPosts() {
        logger.info("Checking for scheduled posts to publish...");
        List<BlogPost> postsToPublish = blogPostRepository.findByStatusAndScheduledTimeBefore(PostStatus.SCHEDULED, Instant.now());
        if (postsToPublish.isEmpty()) {
            return;
        }
        logger.info("Found {} post(s) to publish.", postsToPublish.size());
        for (BlogPost post : postsToPublish) {
            post.setStatus(PostStatus.PUBLISHED);
            blogPostRepository.save(post);
            logger.info("Published post with ID: {} and title: {}", post.getId(), post.getTitle());
        }
    }
}