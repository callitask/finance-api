package com.treishvaam.financeapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "post_thumbnails")
public class PostThumbnail implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "blog_post_id", nullable = false)
  @JsonIgnore // CRITICAL FIX: Breaks infinite recursion (Post -> Thumbnail -> Post)
  private BlogPost blogPost;

  @Column(name = "image_url", nullable = false)
  private String imageUrl;

  @Column(name = "alt_text")
  private String altText;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  // New metadata fields
  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  @Column(name = "mime_type")
  private String mimeType;

  @Column(name = "blur_hash")
  private String blurHash;

  // Getters and Setters

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public BlogPost getBlogPost() {
    return blogPost;
  }

  public void setBlogPost(BlogPost blogPost) {
    this.blogPost = blogPost;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getAltText() {
    return altText;
  }

  public void setAltText(String altText) {
    this.altText = altText;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(int displayOrder) {
    this.displayOrder = displayOrder;
  }

  // Metadata getters/setters

  public Integer getWidth() {
    return width;
  }

  public void setWidth(Integer width) {
    this.width = width;
  }

  public Integer getHeight() {
    return height;
  }

  public void setHeight(Integer height) {
    this.height = height;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public String getBlurHash() {
    return blurHash;
  }

  public void setBlurHash(String blurHash) {
    this.blurHash = blurHash;
  }
}
