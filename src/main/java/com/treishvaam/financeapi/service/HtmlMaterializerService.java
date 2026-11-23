package com.treishvaam.financeapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.security.HtmlSanitizationService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Edge-Side HTML Materialization engine for SEO and fallback caching.
 *
 * <p>Scope: - Scrapes the dynamic internal shell, hydrates it with DB state, and pushes a complete
 * static HTML to the bucket.
 *
 * <p>Critical Dependencies: - FileStorageService (MinIO) - Cloudflare Edge (Worker fetches these
 * materialized files as a last resort)
 *
 * <p>Security Constraints: - Must never materialize PII or administrative views.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • HTML materialization engine for Next.js
 * SEO rendering bypass. - EDITED (Phase 8 GEO Full Execution): • Injected explicitly typed `<link
 * rel="alternate">` tags pointing to the semantic JSON-LD Ontology and LLM Markdown Feeds into the
 * `<head>` of all materialized HTML content. • Why: To ensure AI Crawlers (Google-Extended, OpenAI)
 * landing on materialized HTML instantly trace the entity graph and structured RAG endpoints.
 *
 * <p>- EDITED (P2.2 - Stored XSS Closure, VULN-12): • Escaped `post.getTitle()` via
 * `Entities.escape` in `#server-content` and meta tags; sanitized `post.getContent()` via
 * `htmlSanitizationService.sanitizeHtml(...)` before injecting into `#server-content` (both
 * branches). • Replaced manual string concatenation in `buildJsonLd()` with a `Map<String, Object>`
 * serialized via `objectMapper.writeValueAsString(jsonLdMap)` wrapped in
 * `htmlSanitizationService.escapeForScriptTag(...)`. • Wrapped `convertPostToJson(post)` output in
 * `htmlSanitizationService.escapeForScriptTag(...)` before injecting `window.__PRELOADED_STATE__`.
 * • Replaced `private final RestTemplate restTemplate = new RestTemplate();` with a timeout-bounded
 * RestTemplate (5s connect/read via `SimpleClientHttpRequestFactory`). • Updated cover image URL
 * prefix `/api/v1/uploads/` (was `/api/uploads/`). • Injected `HtmlSanitizationService` via
 * constructor. • Behavior preserved: GEO semantic links, JSON-LD content, and materialization flow
 * intact.
 */
@Service
@Slf4j
public class HtmlMaterializerService {

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final HtmlSanitizationService htmlSanitizationService;
    private final RestTemplate restTemplate;

    public HtmlMaterializerService(
            FileStorageService fileStorageService,
            ObjectMapper objectMapper,
            HtmlSanitizationService htmlSanitizationService) {
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.htmlSanitizationService = htmlSanitizationService;
        RestTemplate timeoutRestTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);
        timeoutRestTemplate.setRequestFactory(requestFactory);
        this.restTemplate = timeoutRestTemplate;
    }

    @Value("${app.frontend.internal-url:http://treishvaam-nginx}")
    private String frontendInternalUrl;

    @Value("${app.frontend.public-url:https://treishvaamfinance.com}")
    private String publicUrl;

    @Async
    public void materializePost(BlogPost post) {
        if (post == null || post.getUserFriendlySlug() == null) {
            return;
        }

        try {
            log.info("Materializing HTML for post: {}", post.getUserFriendlySlug());

            // 1. Fetch the Shell
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

            // 2. Parse
            Document doc = Jsoup.parse(htmlShell);

            // 3. Inject Metadata
            doc.title(Entities.escape(post.getTitle()) + " | Treishvaam Finance");
            doc.select("meta[name=description]").remove();
            doc.head()
                    .appendElement("meta")
                    .attr("name", "description")
                    .attr(
                            "content",
                            post.getMetaDescription() != null
                                    ? post.getMetaDescription()
                                    : post.getTitle());

            updateMeta(doc, "property", "og:title", post.getTitle());
            updateMeta(doc, "property", "og:description", post.getMetaDescription());
            updateMeta(doc, "name", "twitter:title", post.getTitle());
            updateMeta(doc, "name", "twitter:description", post.getMetaDescription());

            if (post.getCoverImageUrl() != null) {
                String imgUrl;
                if (post.getCoverImageUrl().startsWith("/")
                        || post.getCoverImageUrl().startsWith("http")) {
                    imgUrl = post.getCoverImageUrl();
                } else {
                    imgUrl = publicUrl + "/api/v1/uploads/" + post.getCoverImageUrl();
                }
                updateMeta(doc, "property", "og:image", imgUrl);
                updateMeta(doc, "name", "twitter:image", imgUrl);
            }

            // 4. Inject JSON-LD & GEO Semantic Links
            String jsonLd = buildJsonLd(post);
            doc.head().appendElement("script").attr("type", "application/ld+json").html(jsonLd);

            // Explicitly broadcast GEO endpoints to any crawler hitting the raw HTML
            doc.head()
                    .appendElement("link")
                    .attr("rel", "alternate")
                    .attr("type", "application/json+ld")
                    .attr("href", publicUrl + "/api/public/geo/ontology.json");
            doc.head()
                    .appendElement("link")
                    .attr("rel", "alternate")
                    .attr("type", "text/markdown")
                    .attr("href", publicUrl + "/api/public/geo/ai-feed.md");

            // 5. Inject Content (Server-Side Rendering)
            Element serverContentDiv = doc.getElementById("server-content");
            if (serverContentDiv != null) {
                serverContentDiv.removeAttr("style");
                String safeTitle = Entities.escape(post.getTitle());
                String safeContent =
                        htmlSanitizationService.sanitizeHtml(
                                post.getContent() != null ? post.getContent() : "");
                String fullHtmlContent =
                        String.format(
                                "<article class='materialized-content'><h1>%s</h1><div class='prose'>%s</div></article>",
                                safeTitle, safeContent);
                serverContentDiv.html(fullHtmlContent);
            } else {
                doc.body()
                        .append(
                                String.format(
                                        "<div id='server-content'>%s</div>",
                                        htmlSanitizationService.sanitizeHtml(
                                                post.getContent() != null
                                                        ? post.getContent()
                                                        : "")));
            }

            // 6. Pre-load State (Robust Serialization Fix)
            String jsonState = htmlSanitizationService.escapeForScriptTag(convertPostToJson(post));
            String stateScript = String.format("window.__PRELOADED_STATE__ = %s;", jsonState);
            doc.head().appendElement("script").html(stateScript);

            // 7. Upload
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
        if (meta != null) meta.attr("content", content);
        else doc.head().appendElement("meta").attr(attrKey, attrValue).attr("content", content);
    }

    private String buildJsonLd(BlogPost post) {
        try {
            String isoDate = post.getCreatedAt() != null ? post.getCreatedAt().toString() : "";
            String updatedDate =
                    post.getUpdatedAt() != null ? post.getUpdatedAt().toString() : isoDate;
            Map<String, Object> jsonLdMap = new HashMap<>();
            jsonLdMap.put("@context", "https://schema.org");
            jsonLdMap.put("@type", "NewsArticle");
            jsonLdMap.put("headline", post.getTitle());
            jsonLdMap.put("datePublished", isoDate);
            jsonLdMap.put("dateModified", updatedDate);
            Map<String, Object> author = new HashMap<>();
            author.put("@type", "Organization");
            author.put("name", "Treishvaam Finance");
            jsonLdMap.put("author", author);
            String jsonLd = objectMapper.writeValueAsString(jsonLdMap);
            return htmlSanitizationService.escapeForScriptTag(jsonLd);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize JSON-LD for post: {}", post.getUserFriendlySlug());
            return htmlSanitizationService.escapeForScriptTag(
                    "{\"@context\": \"https://schema.org\",\"@type\": \"NewsArticle\"}");
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    private String convertPostToJson(BlogPost post) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("id", post.getId());
            state.put("title", post.getTitle());
            state.put("content", post.getContent());
            state.put("slug", post.getSlug());
            state.put("userFriendlySlug", post.getUserFriendlySlug());
            state.put("urlArticleId", post.getUrlArticleId());
            state.put("metaDescription", post.getMetaDescription());
            state.put("keywords", post.getKeywords());
            state.put("author", post.getAuthor());
            state.put("coverImageUrl", post.getCoverImageUrl());
            state.put("tenantId", post.getTenantId());
            state.put("layoutStyle", post.getLayoutStyle());

            // FIX: Manually convert Instant to String to avoid Jackson configuration issues
            state.put(
                    "createdAt",
                    post.getCreatedAt() != null ? post.getCreatedAt().toString() : null);
            state.put(
                    "updatedAt",
                    post.getUpdatedAt() != null ? post.getUpdatedAt().toString() : null);

            if (post.getCategory() != null) {
                Map<String, Object> cat = new HashMap<>();
                cat.put("id", post.getCategory().getId());
                cat.put("name", post.getCategory().getName());
                cat.put("slug", post.getCategory().getSlug());
                state.put("category", cat);
            }

            return objectMapper.writeValueAsString(state);

        } catch (Exception e) {
            log.error("Failed to serialize post state for hydration: {}", e.getMessage());
            return String.format(
                    "{\"id\":%d, \"title\":\"%s\", \"error\":\"Serialization Failed\"}",
                    post.getId(), escapeJson(post.getTitle()));
        }
    }
}
