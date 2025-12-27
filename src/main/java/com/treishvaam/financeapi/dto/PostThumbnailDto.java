package com.treishvaam.financeapi.dto;

public class PostThumbnailDto {
  private String source; // "new" for uploaded files, "existing" for URLs from post
  private String fileName; // Original name of the uploaded file, to match with MultipartFile
  private String url; // URL for existing images
  private String altText;
  private int displayOrder;

  // Getters and Setters
  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
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
}
