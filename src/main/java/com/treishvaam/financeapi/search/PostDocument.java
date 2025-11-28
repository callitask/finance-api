package com.treishvaam.financeapi.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "blog_posts")
public class PostDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "english")
    private String title;

    @Field(type = FieldType.Text, analyzer = "english")
    private String snippet;

    @Field(type = FieldType.Keyword)
    private String slug;
    
    @Field(type = FieldType.Keyword)
    private String status;

    // --- NEW FIELDS ADDED FOR URL GENERATION ---
    @Field(type = FieldType.Keyword)
    private String categorySlug;

    @Field(type = FieldType.Keyword)
    private String userFriendlySlug;

    @Field(type = FieldType.Keyword)
    private String urlArticleId;

    public PostDocument() {}

    public PostDocument(String id, String title, String snippet, String slug, String status, String categorySlug, String userFriendlySlug, String urlArticleId) {
        this.id = id;
        this.title = title;
        this.snippet = snippet;
        this.slug = slug;
        this.status = status;
        this.categorySlug = categorySlug;
        this.userFriendlySlug = userFriendlySlug;
        this.urlArticleId = urlArticleId;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getCategorySlug() { return categorySlug; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }
    public String getUserFriendlySlug() { return userFriendlySlug; }
    public void setUserFriendlySlug(String userFriendlySlug) { this.userFriendlySlug = userFriendlySlug; }
    public String getUrlArticleId() { return urlArticleId; }
    public void setUrlArticleId(String urlArticleId) { this.urlArticleId = urlArticleId; }
}