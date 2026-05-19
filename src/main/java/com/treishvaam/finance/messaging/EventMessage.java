package com.treishvaam.finance.messaging;

import java.io.Serializable;

public class EventMessage implements Serializable {
  private String eventType;
  private Long entityId;
  private String payload;
  private String source;

  public EventMessage() {}

  public EventMessage(String eventType, Long entityId, String payload) {
    this.eventType = eventType;
    this.entityId = entityId;
    this.payload = payload;
  }

  // ARCH-03 addition for Market Updates
  public EventMessage(String eventType, String source, String payload) {
    this.eventType = eventType;
    this.source = source;
    this.payload = payload;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public Long getEntityId() {
    return entityId;
  }

  public void setEntityId(Long entityId) {
    this.entityId = entityId;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
