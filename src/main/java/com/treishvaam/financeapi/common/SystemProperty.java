package com.treishvaam.financeapi.common;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class SystemProperty {

    @Id
    private String propKey;
    private LocalDateTime propValue;

    public SystemProperty() {}

    public SystemProperty(String propKey, LocalDateTime propValue) {
        this.propKey = propKey;
        this.propValue = propValue;
    }

    // Getters and Setters
    public String getPropKey() { return propKey; }
    public void setPropKey(String propKey) { this.propKey = propKey; }
    public LocalDateTime getPropValue() { return propValue; }
    public void setPropValue(LocalDateTime propValue) { this.propValue = propValue; }
}
