package com.treishvaam.financeapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treishvaam.financeapi.config.CachingConfig;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.PageContent;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.repository.PageContentRepository;
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
    private BlogPostRepository blogPostRepository;

    @Autowired
    private PageContentRepository pageContentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.base-url}")
    private String appBaseUrl;

    // A Base64 encoded SVG for a 1200x675 gray rectangle. This is used as a fallback.
    private static final String GRAY_BANNER_DATA_URI = "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTIwMCIgaGVpZ2h0PSI2NzUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjEwMCUiIGhlaWdodD0iMTAwJSIgZmlsbD0iI2NjY2NjYyIvPjwvc3ZnPg==";
    private static final String DEFAULT_TITLE = "Treishfin · Treishvaam Finance | Financial News & Analysis";
    private static final String DEFAULT_DESCRIPTION = "Stay ahead with the latest financial news, market updates, and expert analysis from Treishvaam Finance. Your daily source for insights on stocks, crypto, and trading.";
    private static final String DEFAULT_OG_TITLE = "Treishfin · Treishvaam Finance | Financial News & Analysis";
    private static final String DEFAULT_OG_DESCRIPTION = "Your daily source for insights on stocks, crypto, and trading.";

    @GetMapping(value = "/blog/{slug}")
    @ResponseBody
    @Cacheable(value = CachingConfig.BLOG_POST_CACHE, key = "#slug")
    public ResponseEntity<String> getPostView(@PathVariable String slug) throws IOException {
        Optional<BlogPost> postOptional = blogPostRepository.findBySlug(slug);
        String htmlContent = readIndexHtml();
        String pageUrl = appBaseUrl + "/blog/" + slug;

        if (postOptional.isPresent()) {
            BlogPost post = postOptional.get();
            String pageTitle = "Treishfin · " + post.getTitle();
            String pageDescription = createSnippet(post.getCustomSnippet() != null && !post.getCustomSnippet().isEmpty() ? post.getCustomSnippet() : post.getContent(), 160);
            
            // UPDATED: Use the gray banner data URI as the fallback image
            String imageUrl = (post.getCoverImageUrl() != null && !post.getCoverImageUrl().isEmpty())
                ? appBaseUrl + "/api/uploads/" + post.getCoverImageUrl() + ".webp"
                : GRAY_BANNER_DATA_URI;

            String articleSchema = generateArticleSchema(post, pageUrl, imageUrl);

            htmlContent = htmlContent
                .replace("__SEO_TITLE__", escapeHtml(pageTitle))
                .replace("__SEO_DESCRIPTION__", escapeHtml(pageDescription))
                .replace("__OG_TITLE__", escapeHtml(pageTitle))
                .replace("__OG_DESCRIPTION__", escapeHtml(pageDescription))
                .replace("__OG_IMAGE__", escapeHtml(imageUrl))
                .replace("__OG_URL__", escapeHtml(pageUrl))
                .replace("__ARTICLE_SCHEMA__", articleSchema);
        } else {
            htmlContent = replaceDefaultTags(htmlContent, pageUrl);
        }

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlContent);
    }

    @GetMapping(value = {"/", "/about", "/services", "/vision", "/education", "/contact", "/login", "/blog"})
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
                .replace("__SEO_TITLE__", escapeHtml(pageTitle))
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
    
    @GetMapping("/ssr-test")
    @ResponseBody
    public String ssrTest() {
        return "SUCCESS: The request reached the Spring Boot backend.";
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
            String logoUrl = appBaseUrl + "/api/logo";
            
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("@context", "https://schema.org");
            schema.put("@type", "Article");
            schema.put("mainEntityOfPage", Map.of(
                "@type", "WebPage",
                "@id", pageUrl
            ));
            schema.put("headline", post.getTitle());
            schema.put("description", createSnippet(post.getCustomSnippet() != null && !post.getCustomSnippet().isEmpty() ? post.getCustomSnippet() : post.getContent(), 160));
            schema.put("image", imageUrl);
            schema.put("author", Map.of(
                "@type", "Person",
                "name", post.getAuthor()
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
            .replace("__SEO_TITLE__", DEFAULT_TITLE)
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