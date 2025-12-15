package com.treishvaam.financeapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FaroPayload {
  private Meta meta;
  private List<Event> events;
  private List<Measurement> measurements;

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

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Meta {
    private App app;
    private Browser browser;
    private Page page;
    private Session session;

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
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class App {
    private String name;
    private String version;

    // getters/setters
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
  public static class Event {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Measurement {
    private String type; // e.g., "web-vitals"
    private Map<String, Object> values; // e.g., {"time_to_first_byte": 123}

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
