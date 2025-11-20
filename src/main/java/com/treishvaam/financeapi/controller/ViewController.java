package com.treishvaam.financeapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.config.CachingConfig;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PageContent;
import com.treishvaam.financeapi.repository.PageContentRepository;
import com.treishvaam.financeapi.service.BlogPostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource; // Added
import org.springframework.core.io.UrlResource; // Added
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path; // Added
import java.nio.file.Paths; // Added
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Controller
public class ViewController {

    @Autowired
    private BlogPostService blogPostService;

    @Autowired
    private PageContentRepository pageContentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.base-url:https://treishfin.treishvaamgroup.com}")
    private String appBaseUrl;
    
    @Value("${app.api-base-url:https://backend.treishvaamgroup.com}")
    private String apiBaseUrl;

    // --- NEW: Inject sitemap storage path ---
    @Value("${storage.sitemap-dir}")
    private String sitemapDir;

    private static final String DEFAULT_TITLE = "Treishfin · Treishvaam Finance | Financial News & Analysis";
    private static final String DEFAULT_DESCRIPTION = "Stay ahead with the latest financial news, market updates, and expert analysis from Treishvaam Finance. Your daily source for insights on stocks, crypto, and trading.";
    private static final String DEFAULT_OG_TITLE = "Treishfin · Treishvaam Finance | Financial News & Analysis";
    private static final String DEFAULT_OG_DESCRIPTION = "Your daily source for insights on stocks, crypto, and trading.";

    private String getDefaultImageUrl() {
        return appBaseUrl + "/logo.webp";
    }
    
    private String getLogoUrl() {
        return appBaseUrl + "/logo.webp";
    }

    /**
     * NEW: Serves the main sitemap index file (sitemap.xml).
     * This fixes the "Sitemap is HTML" error in GSC.
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> serveSitemapIndex() {
        try {
            Path path = Paths.get(sitemapDir, "sitemap.xml");
            Resource resource = new UrlResource(path.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * NEW: Serves individual sitemap files (e.g., posts-1.xml, categories.xml).
     */
    @GetMapping(value = "/sitemaps/{fileName}", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> serveSitemapFile(@PathVariable String fileName) {
        // Security check: Prevent directory traversal attacks and ensure XML extension
        if (fileName.contains("..") || !fileName.endsWith(".xml")) {
             return ResponseEntity.badRequest().build();
        }
        
        try {
            Path path = Paths.get(sitemapDir, fileName);
            Resource resource = new UrlResource(path.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Serves dynamic blog posts with Server-Side SEO injection and State Hydration.
     * Cached for 1 hour to balance performance with freshness.
     */
    @GetMapping(value = "/category/{categorySlug}/{userFriendlySlug}/{urlArticleId}")
    @ResponseBody
    @Cacheable(value = CachingConfig.BLOG_POST_CACHE, key = "#urlArticleId")
    public ResponseEntity<String> getPostView(
            @PathVariable String categorySlug,
            @PathVariable String userFriendlySlug,
            @PathVariable String urlArticleId) throws IOException {
        
        Optional<BlogPost> postOptional = blogPostService.findByUrlArticleId(urlArticleId);
        String htmlContent = readIndexHtml();
        
        // Construct the strict canonical URL
        String pageUrl = String.format("%s/category/%s/%s/%s", appBaseUrl, categorySlug, userFriendlySlug, urlArticleId);

        if (postOptional.isPresent()) {
            BlogPost post = postOptional.get();
            String pageTitle = "Treishfin · " + post.getTitle();
            String pageDescription = post.getMetaDescription() != null && !post.getMetaDescription().isEmpty()
                ? post.getMetaDescription()
                : createSnippet(post.getCustomSnippet() != null && !post.getCustomSnippet().isEmpty() ? post.getCustomSnippet() : post.getContent(), 160);
            
            String imageUrl = (post.getCoverImageUrl() != null && !post.getCoverImageUrl().isEmpty())
                ? apiBaseUrl + "/api/uploads/" + post.getCoverImageUrl() + ".webp"
                : getDefaultImageUrl();

            String articleSchema = generateArticleSchema(post, pageUrl, imageUrl);

            // Serialize Post Object to JSON for Frontend Hydration
            String postJson = objectMapper.writeValueAsString(post);
            // Sanitize JSON to prevent script injection attacks via the content
            postJson = postJson.replace("</script>", "<\\/script>");

            // Inject Metadata, Schema, Content AND JSON State into the HTML
            htmlContent = htmlContent
                .replace("<title>__SEO_TITLE__</title>", 
                         "<title>" + escapeHtml(pageTitle) + "</title><link rel=\"canonical\" href=\"" + escapeHtml(pageUrl) + "\" />")
                .replace("__SEO_DESCRIPTION__", escapeHtml(pageDescription))
                .replace("__OG_TITLE__", escapeHtml(post.getTitle()))
                .replace("__OG_DESCRIPTION__", escapeHtml(pageDescription))
                .replace("__OG_IMAGE__", escapeHtml(imageUrl))
                .replace("__OG_URL__", escapeHtml(pageUrl))
                .replace("__ARTICLE_SCHEMA__", articleSchema)
                // Inject actual body content for non-JS bots
                .replace("__PAGE_CONTENT__", escapeHtml(post.getContent()))
                // Inject JSON State for React Hydration
                .replace("//__PRELOADED_STATE__", "window.__PRELOADED_STATE__ = " + postJson + ";");

        } else {
            // Fallback if post not found (let React handle 404 UI, but serve valid HTML)
            htmlContent = replaceDefaultTags(htmlContent, pageUrl);
        }

        return ResponseEntity.ok()
                // Tell Googlebot and Browsers to cache this HTML for 1 hour
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .contentType(MediaType.TEXT_HTML)
                .body(htmlContent);
    }

    /**
     * Serves static pages (About, Vision, etc.).
     * Cached for 24 hours as these change rarely.
     */
    @GetMapping(value = {"/", "/about", "/services", "/vision", "/education", "/contact", "/login"})
    @ResponseBody
    public ResponseEntity<String> serveStaticPage(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String pageName = path.equals("/") ? "index" : path.substring(1);
        String pageUrl = appBaseUrl + path;

        Optional<PageContent> pageContentOptional = pageContentRepository.findById(pageName);
        String htmlContent = readIndexHtml();

        if (pageContentOptional.isPresent()) {
            PageContent page = pageContentOptional.get();
            String pageTitle = "Treishfin · " + page.getTitle();
            String pageDescription = createSnippet(page.getContent(), 160);
            String webPageSchema = generateWebPageSchema(page, pageUrl);

            htmlContent = htmlContent
                .replace("<title>__SEO_TITLE__</title>", 
                         "<title>" + escapeHtml(pageTitle) + "</title><link rel=\"canonical\" href=\"" + escapeHtml(pageUrl) + "\" />")
                .replace("__SEO_DESCRIPTION__", escapeHtml(pageDescription))
                .replace("__OG_TITLE__", escapeHtml(pageTitle))
                .replace("__OG_DESCRIPTION__", escapeHtml(pageDescription))
                .replace("__OG_IMAGE__", getDefaultImageUrl())
                .replace("__OG_URL__", escapeHtml(pageUrl))
                .replace("__ARTICLE_SCHEMA__", webPageSchema)
                .replace("__PAGE_CONTENT__", escapeHtml(page.getContent()))
                .replace("//__PRELOADED_STATE__", ""); // Clean up placeholder
        } else {
            htmlContent = replaceDefaultTags(htmlContent, pageUrl);
        }
        
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS))
                .contentType(MediaType.TEXT_HTML)
                .body(htmlContent);
    }

    @GetMapping(value = "/dashboard/**")
    @ResponseBody
    public ResponseEntity<String> forwardToDashboard() throws IOException {
        String htmlContent = readIndexHtml();
        htmlContent = replaceDefaultTags(htmlContent, appBaseUrl + "/dashboard");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.TEXT_HTML)
                .body(htmlContent);
    }
    
    private String generateWebPageSchema(PageContent page, String pageUrl) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "@context", "https://schema.org",
                "@type", "WebPage",
                "name", page.getTitle(),
                "description", createSnippet(page.getContent(), 160),
                "url", pageUrl
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String generateArticleSchema(BlogPost post, String pageUrl, String imageUrl) {
        try {
            String logoUrl = getLogoUrl();
            
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("@context", "https://schema.org");
            schema.put("@type", "Article");
            schema.put("mainEntityOfPage", Map.of(
                "@type", "WebPage",
                "@id", pageUrl
            ));
            schema.put("headline", post.getTitle());
            schema.put("description", post.getMetaDescription() != null && !post.getMetaDescription().isEmpty() ? post.getMetaDescription() : createSnippet(post.getContent(), 160));
            schema.put("image", imageUrl);
            schema.put("author", Map.of(
                "@type", "Organization",
                "name", "Treishvaam Finance",
                "url", appBaseUrl
            ));
            schema.put("publisher", Map.of(
                "@type", "Organization",
                "name", "Treishvaam Finance",
                "logo", Map.of(
                    "@type", "ImageObject",
                    "url", logoUrl
                )
            ));
            schema.put("datePublished", post.getCreatedAt().toString());
            schema.put("dateModified", post.getUpdatedAt().toString());

            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    private String replaceDefaultTags(String html, String pageUrl) {
        return html
            .replace("<title>__SEO_TITLE__</title>", 
                     "<title>" + DEFAULT_TITLE + "</title><link rel=\"canonical\" href=\"" + escapeHtml(pageUrl) + "\" />")
            .replace("__SEO_DESCRIPTION__", DEFAULT_DESCRIPTION)
            .replace("__OG_TITLE__", DEFAULT_OG_TITLE)
            .replace("__OG_DESCRIPTION__", DEFAULT_OG_DESCRIPTION)
            .replace("__OG_IMAGE__", getDefaultImageUrl())
            .replace("__OG_URL__", pageUrl)
            .replace("__ARTICLE_SCHEMA__", "{}")
            .replace("__PAGE_CONTENT__", "")
            .replace("//__PRELOADED_STATE__", ""); // Clean up placeholder
    }

    private String readIndexHtml() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/index.html");
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }

    private String createSnippet(String html, int length) {
        if (html == null || html.isEmpty()) return "";
        String plainText = html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (plainText.length() <= length) return plainText;
        String trimmed = plainText.substring(0, length);
        int lastSpace = trimmed.lastIndexOf(' ');
        return (lastSpace > 0 ? trimmed.substring(0, lastSpace) : trimmed) + "...";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}