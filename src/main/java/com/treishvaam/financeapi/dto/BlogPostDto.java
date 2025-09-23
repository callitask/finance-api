package com.treishvaam.financeapi.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public class BlogPostDto {
    private Long id;
    private String title;
    private String content;
    private String customSnippet;
    private String metaDescription;
    private String keywords; // NEW FIELD
    private String author;
    private String category;
    private List<String> tags;
    private String thumbnailUrl;
    private String coverImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean featured;
    private Instant scheduledTime;
    private boolean published;
    private String layoutStyle;
    private String layoutGroupId;
    private String userFriendlySlug;
    private String urlArticleId;

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCustomSnippet() { return customSnippet; }
    public void setCustomSnippet(String customSnippet) { this.customSnippet = customSnippet; }
    public String getMetaDescription() { return metaDescription; }
    public void setMetaDescription(String metaDescription) { this.metaDescription = metaDescription; }
    public String getKeywords() { return keywords; } // NEW GETTER
    public void setKeywords(String keywords) { this.keywords = keywords; } // NEW SETTER
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public String getLayoutStyle() { return layoutStyle; }
    public void setLayoutStyle(String layoutStyle) { this.layoutStyle = layoutStyle; }
    public String getLayoutGroupId() { return layoutGroupId; }
    public void setLayoutGroupId(String layoutGroupId) { this.layoutGroupId = layoutGroupId; }
    public String getUserFriendlySlug() { return userFriendlySlug; }
    public void setUserFriendlySlug(String userFriendlySlug) { this.userFriendlySlug = userFriendlySlug; }
    public String getUrlArticleId() { return urlArticleId; }
    public void setUrlArticleId(String urlArticleId) { this.urlArticleId = urlArticleId; }
}