package com.treishvaam.financeapi.dto;

public class BlogPostSuggestionDto {
    private Long id;
    private String title;
    private String slug;

    public BlogPostSuggestionDto(Long id, String title, String slug) {
        this.id = id;
        this.title = title;
        this.slug = slug;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }
}