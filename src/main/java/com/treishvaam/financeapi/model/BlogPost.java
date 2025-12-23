package com.treishvaam.financeapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "blog_posts")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class BlogPost {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(unique = true)
  private String slug;

  @Column(name = "user_friendly_slug")
  private String userFriendlySlug;

  @Lob
  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Lob
  @Column(columnDefinition = "TEXT")
  private String customSnippet;

  @Lob
  @Column(columnDefinition = "TEXT")
  private String metaDescription;

  @Column(length = 512)
  private String keywords;

  // --- NEW ENTERPRISE SEO FIELDS ---
  @Column(name = "seo_title")
  private String seoTitle;

  @Column(name = "canonical_url")
  private String canonicalUrl;

  @Column(name = "focus_keyword")
  private String focusKeyword;

  // --- NEW EDITORIAL SECTION (FIXED: Explicitly defined as VARCHAR) ---
  @Enumerated(EnumType.STRING)
  @Column(name = "display_section", columnDefinition = "VARCHAR(50)")
  private DisplaySection displaySection = DisplaySection.STANDARD;

  @Column(nullable = false)
  private String author;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "category_id")
  private Category category;

  @Column private boolean featured;

  @JsonFormat(
      shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
      timezone = "UTC")
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @JsonFormat(
      shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
      timezone = "UTC")
  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @OneToMany(
      mappedBy = "blogPost",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("displayOrder ASC")
  private List<PostThumbnail> thumbnails = new ArrayList<>();

  @Column(name = "thumbnail_orientation")
  private String thumbnailOrientation;

  @Column(name = "cover_image_url")
  private String coverImageUrl;

  @Column(name = "cover_image_alt_text")
  private String coverImageAltText;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
  @Column(name = "tag")
  private List<String> tags;

  @JsonFormat(
      shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
      timezone = "UTC")
  @Column(name = "scheduled_time")
  private Instant scheduledTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PostStatus status = PostStatus.DRAFT;

  @Column(name = "layout_style")
  private String layoutStyle = "DEFAULT";

  @Column(name = "layout_group_id")
  private String layoutGroupId;

  @Column(name = "url_article_id")
  private String urlArticleId;

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    if (this.displaySection == null) {
      this.displaySection = DisplaySection.STANDARD;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
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

  public String getUserFriendlySlug() {
    return userFriendlySlug;
  }

  public void setUserFriendlySlug(String userFriendlySlug) {
    this.userFriendlySlug = userFriendlySlug;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getCustomSnippet() {
    return customSnippet;
  }

  public void setCustomSnippet(String customSnippet) {
    this.customSnippet = customSnippet;
  }

  public String getMetaDescription() {
    return metaDescription;
  }

  public void setMetaDescription(String metaDescription) {
    this.metaDescription = metaDescription;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public Category getCategory() {
    return category;
  }

  public void setCategory(Category category) {
    this.category = category;
  }

  public boolean isFeatured() {
    return featured;
  }

  public void setFeatured(boolean featured) {
    this.featured = featured;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getScheduledTime() {
    return scheduledTime;
  }

  public void setScheduledTime(Instant scheduledTime) {
    this.scheduledTime = scheduledTime;
  }

  public PostStatus getStatus() {
    return status;
  }

  public void setStatus(PostStatus status) {
    this.status = status;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public List<PostThumbnail> getThumbnails() {
    return thumbnails;
  }

  public void setThumbnails(List<PostThumbnail> thumbnails) {
    this.thumbnails = thumbnails;
  }

  public String getThumbnailOrientation() {
    return thumbnailOrientation;
  }

  public void setThumbnailOrientation(String thumbnailOrientation) {
    this.thumbnailOrientation = thumbnailOrientation;
  }

  public String getCoverImageUrl() {
    return coverImageUrl;
  }

  public void setCoverImageUrl(String coverImageUrl) {
    this.coverImageUrl = coverImageUrl;
  }

  public String getCoverImageAltText() {
    return coverImageAltText;
  }

  public void setCoverImageAltText(String coverImageAltText) {
    this.coverImageAltText = coverImageAltText;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public String getLayoutStyle() {
    return layoutStyle;
  }

  public void setLayoutStyle(String layoutStyle) {
    this.layoutStyle = layoutStyle;
  }

  public String getLayoutGroupId() {
    return layoutGroupId;
  }

  public void setLayoutGroupId(String layoutGroupId) {
    this.layoutGroupId = layoutGroupId;
  }

  public String getUrlArticleId() {
    return urlArticleId;
  }

  public void setUrlArticleId(String urlArticleId) {
    this.urlArticleId = urlArticleId;
  }

  // New SEO & Section Getters/Setters
  public String getSeoTitle() {
    return seoTitle;
  }

  public void setSeoTitle(String seoTitle) {
    this.seoTitle = seoTitle;
  }

  public String getCanonicalUrl() {
    return canonicalUrl;
  }

  public void setCanonicalUrl(String canonicalUrl) {
    this.canonicalUrl = canonicalUrl;
  }

  public String getFocusKeyword() {
    return focusKeyword;
  }

  public void setFocusKeyword(String focusKeyword) {
    this.focusKeyword = focusKeyword;
  }

  public DisplaySection getDisplaySection() {
    return displaySection;
  }

  public void setDisplaySection(DisplaySection displaySection) {
    this.displaySection = displaySection;
  }
}
