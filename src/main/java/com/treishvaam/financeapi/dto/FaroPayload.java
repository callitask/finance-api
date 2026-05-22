/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Data Transfer Object for mapping Grafana Faro Real User Monitoring (RUM) payloads.
 *
 * <p>Scope: - Responsible for deserializing telemetry JSON from frontend clients. - Must never
 * execute or directly evaluate payload contents to prevent injection.
 *
 * <p>Critical Dependencies: - Backend: MonitoringController ingest endpoint. - Frontend:
 * faroConfig.js payload structure.
 *
 * <p>Security Constraints: - ignoreUnknown = true is required to prevent crash loops when Faro
 * updates its SDK payload schema.
 *
 * <p>Non-Negotiables: - Must map standard Faro entities exactly as defined by
 * the @grafana/faro-web-sdk.
 *
 * <p>Change Intent: - Add mapping for event attributes to capture custom data (resolution, UTM
 * source) sent via faro.api.pushEvent.
 *
 * <p>Future AI Guidance: - Never remove the 'ignoreUnknown = true' annotation.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • Added `attributes` map to the `Event`
 * class. • Reason: Grafana Faro's pushEvent method stores custom payload data inside
 * event.attributes, not the root extra map. Required for exact resolution and traffic source
 * mapping.
 */
package com.treishvaam.financeapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FaroPayload {
    private Meta meta;
    private List<Event> events;
    private List<Measurement> measurements;
    // Added: Capture intelligent source tracking from Frontend (root level)
    private Map<String, String> extra;

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public List<Measurement> getMeasurements() {
        return measurements;
    }

    public void setMeasurements(List<Measurement> measurements) {
        this.measurements = measurements;
    }

    public Map<String, String> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, String> extra) {
        this.extra = extra;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private App app;
        private Browser browser;
        private Page page;
        private Session session;
        private User user;

        public App getApp() {
            return app;
        }

        public void setApp(App app) {
            this.app = app;
        }

        public Browser getBrowser() {
            return browser;
        }

        public void setBrowser(Browser browser) {
            this.browser = browser;
        }

        public Page getPage() {
            return page;
        }

        public void setPage(Page page) {
            this.page = page;
        }

        public Session getSession() {
            return session;
        }

        public void setSession(Session session) {
            this.session = session;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class App {
        private String name;
        private String version;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Browser {
        private String name;
        private String version;
        private String os;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getOs() {
            return os;
        }

        public void setOs(String os) {
            this.os = os;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Page {
        private String url;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Session {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        private String id;
        private String username;
        private String email;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {
        private String name;
        private Map<String, String> attributes; // Capture detailed event parameters

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Measurement {
        private String type;
        private Map<String, Object> values;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, Object> getValues() {
            return values;
        }

        public void setValues(Map<String, Object> values) {
            this.values = values;
        }
    }
}
