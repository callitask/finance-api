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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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

    private static final String GRAY_BANNER_DATA_URI = "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTIwMCIgaGVpZ2h0PSI2NzUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjEwMCUiIGhlaWdodD0iMTAwJSIgZmlsbD0iI2NjY2NjYyIvPjwvc3ZnPg==";
    private static final String DEFAULT_TITLE = "Treishfin · Treishvaam Finance | Financial News & Analysis";
    private static final String DEFAULT_DESCRIPTION = "Stay ahead with the latest financial news, market updates, and expert analysis from Treishvaam Finance. Your daily source for insights on stocks, crypto, and trading.";
    private static final String DEFAULT_OG_TITLE = "Treishfin · Treishvaam Finance | Financial News & Analysis";
    private static final String DEFAULT_OG_DESCRIPTION = "Your daily source for insights on stocks, crypto, and trading.";

    @GetMapping(value = "/category/{categorySlug}/{userFriendlySlug}/{urlArticleId}")
    @ResponseBody
    @Cacheable(value = CachingConfig.BLOG_POST_CACHE, key = "#urlArticleId")
    public ResponseEntity<String> getPostView(
            @PathVariable String categorySlug,
            @PathVariable String userFriendlySlug,
            @PathVariable String urlArticleId) throws IOException {
        
        Optional<BlogPost> postOptional = blogPostService.findByUrlArticleId(urlArticleId);
        String htmlContent = readIndexHtml();
        String pageUrl = String.format("%s/category/%s/%s/%s", appBaseUrl, categorySlug, userFriendlySlug, urlArticleId);

        if (postOptional.isPresent()) {
            BlogPost post = postOptional.get();
            String pageTitle = "Treishfin · " + post.getTitle();
            String pageDescription = post.getMetaDescription() != null && !post.getMetaDescription().isEmpty()
                ? post.getMetaDescription()
                : createSnippet(post.getCustomSnippet() != null && !post.getCustomSnippet().isEmpty() ? post.getCustomSnippet() : post.getContent(), 160);
            
            String imageUrl = (post.getCoverImageUrl() != null && !post.getCoverImageUrl().isEmpty())
                ? apiBaseUrl + "/api/uploads/" + post.getCoverImageUrl() + ".webp"
                : GRAY_BANNER_DATA_URI;

            String articleSchema = generateArticleSchema(post, pageUrl, imageUrl);

            htmlContent = htmlContent
                .replace("<title>__SEO_TITLE__</title>", 
                         "<title>" + escapeHtml(pageTitle) + "</title><link rel=\"canonical\" href=\"" + escapeHtml(pageUrl) + "\" />")
                .replace("__SEO_DESCRIPTION__", escapeHtml(pageDescription))
                .replace("__OG_TITLE__", escapeHtml(post.getTitle()))
                .replace("__OG_DESCRIPTION__", escapeHtml(pageDescription))
                .replace("__OG_IMAGE__", escapeHtml(imageUrl))
                .replace("__OG_URL__", escapeHtml(pageUrl))
                .replace("__ARTICLE_SCHEMA__", articleSchema);
        } else {
            htmlContent = replaceDefaultTags(htmlContent, pageUrl);
        }

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlContent);
    }

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
                .replace("__OG_IMAGE__", GRAY_BANNER_DATA_URI)
                .replace("__OG_URL__", escapeHtml(pageUrl))
                .replace("__ARTICLE_SCHEMA__", webPageSchema);
        } else {
            htmlContent = replaceDefaultTags(htmlContent, pageUrl);
        }
        
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlContent);
    }

    @GetMapping(value = "/dashboard/**")
    @ResponseBody
    public ResponseEntity<String> forwardToDashboard() throws IOException {
        String htmlContent = readIndexHtml();
        htmlContent = replaceDefaultTags(htmlContent, appBaseUrl + "/dashboard");
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlContent);
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
            String logoUrl = appBaseUrl + "/logo512.png";
            
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
                    "@type", "ImageObject", // CORRECTED: Changed ':' to ','
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
            .replace("__OG_IMAGE__", GRAY_BANNER_DATA_URI)
            .replace("__OG_URL__", pageUrl)
            .replace("__ARTICLE_SCHEMA__", "{}");
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