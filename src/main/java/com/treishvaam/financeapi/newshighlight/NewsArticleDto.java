package com.treishvaam.financeapi.newshighlight;

public class NewsArticleDto {
  private String title;
  private String link;
  private String source_id;
  private String pubDate;

  public NewsArticleDto() {}

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getSource_id() {
    return source_id;
  }

  public void setSource_id(String source_id) {
    this.source_id = source_id;
  }

  public String getPubDate() {
    return pubDate;
  }

  public void setPubDate(String pubDate) {
    this.pubDate = pubDate;
  }
}
