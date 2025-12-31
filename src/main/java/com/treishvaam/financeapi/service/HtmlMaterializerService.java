package com.treishvaam.financeapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.model.BlogPost;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class HtmlMaterializerService {

  private final FileStorageService fileStorageService;
  private final ObjectMapper objectMapper; // ENTERPRISE: Use Jackson for robust serialization
  private final RestTemplate restTemplate = new RestTemplate();

  // Internal Docker URL to fetch the current Frontend Shell from Nginx
  // Ensures we match the currently deployed frontend version
  @Value("${app.frontend.internal-url:http://treishvaam-nginx}")
  private String frontendInternalUrl;

  @Value("${app.frontend.public-url:https://treishfin.treishvaamgroup.com}")
  private String publicUrl;

  /**
   * Core method to generate and upload static HTML Runs asynchronously to not block the user
   * response.
   */
  @Async
  public void materializePost(BlogPost post) {
    if (post == null || post.getUserFriendlySlug() == null) {
      return;
    }

    try {
      log.info("Materializing HTML for post: {}", post.getUserFriendlySlug());

      // 1. Fetch the Shell (index.html) from Nginx
      String htmlShell;
      try {
        htmlShell = restTemplate.getForObject(frontendInternalUrl + "/", String.class);
      } catch (Exception e) {
        log.warn("Could not fetch internal frontend shell. Trying public URL.");
        htmlShell = restTemplate.getForObject(publicUrl + "/", String.class);
      }

      if (htmlShell == null || htmlShell.isEmpty()) {
        log.error("Failed to fetch HTML shell. Aborting materialization.");
        return;
      }

      // 2. Parse with Jsoup
      Document doc = Jsoup.parse(htmlShell);

      // 3. Inject SEO Metadata (Overrides generic template tags)
      doc.title(post.getTitle() + " | Treishfin");

      // Clean existing generic description
      doc.select("meta[name=description]").remove();
      doc.head()
          .appendElement("meta")
          .attr("name", "description")
          .attr(
              "content",
              post.getMetaDescription() != null ? post.getMetaDescription() : post.getTitle());

      // Open Graph & Twitter
      updateMeta(doc, "property", "og:title", post.getTitle());
      updateMeta(doc, "property", "og:description", post.getMetaDescription());
      updateMeta(doc, "name", "twitter:title", post.getTitle());
      updateMeta(doc, "name", "twitter:description", post.getMetaDescription());

      if (post.getCoverImageUrl() != null) {
        String imgUrl =
            post.getCoverImageUrl().startsWith("http")
                ? post.getCoverImageUrl()
                : publicUrl + "/api/uploads/" + post.getCoverImageUrl();
        updateMeta(doc, "property", "og:image", imgUrl);
        updateMeta(doc, "name", "twitter:image", imgUrl);
      }

      // 4. Inject JSON-LD Schema (Critical for Google News)
      String jsonLd = buildJsonLd(post);
      doc.head().appendElement("script").attr("type", "application/ld+json").html(jsonLd);

      // 5. Inject Content (Server-Side Rendering)
      // We inject into #server-content AND un-hide it so Google sees it immediately
      Element serverContentDiv = doc.getElementById("server-content");
      if (serverContentDiv != null) {
        serverContentDiv.removeAttr("style"); // Remove 'display:none'

        String fullHtmlContent =
            String.format(
                "<article class='materialized-content'><h1>%s</h1><div class='prose'>%s</div></article>",
                post.getTitle(), post.getContent() != null ? post.getContent() : "");
        serverContentDiv.html(fullHtmlContent);
      } else {
        // Fallback if index.html hasn't been updated with #server-content yet
        doc.body().append(String.format("<div id='server-content'>%s</div>", post.getContent()));
      }

      // 6. Pre-load State for React (Hydration)
      // FIX: Serialize the FULL state so React doesn't crash on hydration
      String jsonState = convertPostToJson(post);
      String stateScript = String.format("window.__PRELOADED_STATE__ = %s;", jsonState);
      doc.head().appendElement("script").html(stateScript);

      // 7. Upload to MinIO
      byte[] contentBytes = doc.html().getBytes(StandardCharsets.UTF_8);
      String filePath = "posts/" + post.getUserFriendlySlug() + ".html";

      fileStorageService.uploadHtmlFile(
          filePath, new ByteArrayInputStream(contentBytes), contentBytes.length);

      log.info("Successfully materialized: {}", filePath);

    } catch (Exception e) {
      log.error("Error materializing post: {}", e.getMessage(), e);
    }
  }

  private void updateMeta(Document doc, String attrKey, String attrValue, String content) {
    if (content == null) return;
    Element meta = doc.select("meta[" + attrKey + "=" + attrValue + "]").first();
    if (meta != null) {
      meta.attr("content", content);
    } else {
      doc.head().appendElement("meta").attr(attrKey, attrValue).attr("content", content);
    }
  }

  private String buildJsonLd(BlogPost post) {
    String isoDate = post.getCreatedAt() != null ? post.getCreatedAt().toString() : "";
    String updatedDate = post.getUpdatedAt() != null ? post.getUpdatedAt().toString() : isoDate;

    return String.format(
        "{"
            + "\"@context\": \"https://schema.org\","
            + "\"@type\": \"NewsArticle\","
            + "\"headline\": \"%s\","
            + "\"datePublished\": \"%s\","
            + "\"dateModified\": \"%s\","
            + "\"author\": {\"@type\": \"Organization\", \"name\": \"Treishvaam Finance\"}"
            + "}",
        escapeJson(post.getTitle()), isoDate, updatedDate);
  }

  private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
  }

  private String convertPostToJson(BlogPost post) {
    try {
      // ENTERPRISE FIX: Create a clean Map of all fields React needs.
      // We manually map this to avoid Hibernate LazyLoading exceptions or circular references.
      Map<String, Object> state = new HashMap<>();
      state.put("id", post.getId());
      state.put("title", post.getTitle());
      state.put("content", post.getContent()); // CRITICAL: Required for hydration
      state.put("slug", post.getSlug());
      state.put("userFriendlySlug", post.getUserFriendlySlug());
      state.put("urlArticleId", post.getUrlArticleId());
      state.put("metaDescription", post.getMetaDescription());
      state.put("keywords", post.getKeywords());
      state.put("author", post.getAuthor());
      state.put("coverImageUrl", post.getCoverImageUrl());
      state.put("createdAt", post.getCreatedAt());
      state.put("updatedAt", post.getUpdatedAt());
      state.put("tenantId", post.getTenantId());
      state.put("layoutStyle", post.getLayoutStyle());

      if (post.getCategory() != null) {
        Map<String, Object> cat = new HashMap<>();
        cat.put("id", post.getCategory().getId());
        cat.put("name", post.getCategory().getName());
        cat.put("slug", post.getCategory().getSlug());
        state.put("category", cat);
      }

      // Serialize to JSON using Jackson
      return objectMapper.writeValueAsString(state);

    } catch (Exception e) {
      log.error("Failed to serialize post state for hydration: {}", e.getMessage());
      // Fallback to minimal state to prevent total crash, though content will be missing
      return String.format(
          "{\"id\":%d, \"title\":\"%s\", \"error\":\"Serialization Failed\"}",
          post.getId(), escapeJson(post.getTitle()));
    }
  }
}