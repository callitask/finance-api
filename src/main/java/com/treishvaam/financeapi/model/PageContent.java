package com.treishvaam.financeapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "page_content")
public class PageContent {

    @Id
    @Column(name = "page_name", unique = true, nullable = false)
    private String pageName; // e.g., "about", "vision", "index"

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Getters and Setters
    public String getPageName() {
        return pageName;
    }

    public void setPageName(String pageName) {
        this.pageName = pageName;
    }

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
}