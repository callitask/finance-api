package com.treishvaam.financeapi.dto;

public class BlogPostSuggestionDto {
    private Long id;
    private String title;
    private String slug; // Kept for legacy compatibility
    
    // --- NEW FIELDS FOR URL CONSTRUCTION ---
    private String categorySlug;
    private String userFriendlySlug;
    private String urlArticleId;

    public BlogPostSuggestionDto(Long id, String title, String slug, String categorySlug, String userFriendlySlug, String urlArticleId) {
        this.id = id;
        this.title = title;
        this.slug = slug;
        this.categorySlug = categorySlug;
        this.userFriendlySlug = userFriendlySlug;
        this.urlArticleId = urlArticleId;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    
    public String getCategorySlug() { return categorySlug; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }
    public String getUserFriendlySlug() { return userFriendlySlug; }
    public void setUserFriendlySlug(String userFriendlySlug) { this.userFriendlySlug = userFriendlySlug; }
    public String getUrlArticleId() { return urlArticleId; }
    public void setUrlArticleId(String urlArticleId) { this.urlArticleId = urlArticleId; }
}