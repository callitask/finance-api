package com.treishvaam.financeapi.newshighlight.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsDataArticle {
  private String title;
  private String link;
  private List<String> keywords;
  private List<String> creator;

  @JsonProperty("video_url")
  private String videoUrl;

  @JsonProperty("description")
  private String description;

  @JsonProperty("content")
  private String content;

  @JsonProperty("pubDate")
  private String pubDate;

  @JsonProperty("image_url")
  private String imageUrl;

  @JsonProperty("source_id")
  private String sourceId;

  @JsonProperty("source_priority")
  private int sourcePriority;

  private List<String> country;
  private List<String> category;
  private String language;

  // Getters
  public String getTitle() {
    return title;
  }

  public String getLink() {
    return link;
  }

  public String getDescription() {
    return description;
  }

  public String getPubDate() {
    return pubDate;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public String getSourceId() {
    return sourceId;
  }

  // Setters
  public void setTitle(String title) {
    this.title = title;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setPubDate(String pubDate) {
    this.pubDate = pubDate;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }
}
