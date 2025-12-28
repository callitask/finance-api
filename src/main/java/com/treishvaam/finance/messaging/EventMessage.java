package com.treishvaam.financeapi.messaging;

import java.io.Serializable;

public class EventMessage implements Serializable {
  private String eventType;
  private Long entityId;
  private String payload;

  public EventMessage() {}

  public EventMessage(String eventType, Long entityId, String payload) {
    this.eventType = eventType;
    this.entityId = entityId;
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
}