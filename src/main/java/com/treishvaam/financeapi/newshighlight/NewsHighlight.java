package com.treishvaam.financeapi.newshighlight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "news_highlights",
    indexes = {
      @Index(name = "idx_news_archived_published", columnList = "is_archived, published_at"),
      @Index(name = "idx_news_link", columnList = "link", unique = true)
    })
public class NewsHighlight {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 500)
  private String title;

  // Description for SEO/Preview text
  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false, length = 1000)
  private String link;

  @Column(length = 100)
  private String source;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "image_url", length = 1000)
  private String imageUrl;

  @Column(name = "is_archived", nullable = false)
  private boolean isArchived = false;

  public NewsHighlight() {}

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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public LocalDateTime getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(LocalDateTime publishedAt) {
    this.publishedAt = publishedAt;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public boolean isArchived() {
    return isArchived;
  }

  public void setArchived(boolean archived) {
    isArchived = archived;
  }
}
