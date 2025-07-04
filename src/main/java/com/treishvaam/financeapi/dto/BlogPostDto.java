package com.treishvaam.financeapi.dto;

public class BlogPostDto { 
    private String title; 
    private String content; 
    private String author; 
    private String category;
    private boolean isFeatured;

    // Getters and Setters
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public String getAuthor() {
        return author;
    }
    public void setAuthor(String author) {
        this.author = author;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public boolean isFeatured() {
        return isFeatured;
    }
    public void setFeatured(boolean isFeatured) {
        this.isFeatured = isFeatured;
    }
}
