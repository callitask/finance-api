package com.treishvaam.financeapi.newshighlight;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsArticleDto implements Serializable {

  private String title;

  // Support multiple field names from different APIs (FMP, NewsAPI)
  @JsonAlias({"image", "urlToImage", "imageUrl"})
  private String image;

  @JsonAlias({"site", "source", "author"})
  private String source;

  @JsonAlias({"text", "description", "summary"})
  private String text;

  @JsonAlias({"url", "link"})
  private String link;

  @JsonAlias({"publishedDate", "date", "publishedAt"})
  private String date;

  // Getters and Setters
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }
}
