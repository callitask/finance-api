package com.treishvaam.financeapi.dto;

import java.util.List;

public class ShareRequest {

  private String message;
  private List<String> tags;

  // Getters and Setters
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }
}
