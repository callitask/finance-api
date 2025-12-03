package com.treishvaam.financeapi.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventMessage implements Serializable {
    private String eventType; // e.g., "INDEX_POST", "DELETE_POST"
    private Long entityId;    // e.g., Post ID
    private String payload;   // Extra data if needed
}