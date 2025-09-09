package com.treishvaam.financeapi.newshighlight;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_highlights", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"link"})
})
public class NewsHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(nullable = false, length = 1024)
    private String link;

    private String source;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt = LocalDateTime.now();

    // *** CRITICAL FIX: Added default constructor and manual getters/setters ***
    
    // Default (no-argument) constructor required by Hibernate
    public NewsHighlight() {}

    // Constructor for creating new instances
    public NewsHighlight(String title, String link, String source, LocalDateTime publishedAt) {
        this.title = title;
        this.link = link;
        this.source = source;
        this.publishedAt = publishedAt;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}