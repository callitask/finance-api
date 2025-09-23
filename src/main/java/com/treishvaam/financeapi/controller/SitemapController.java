package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.CategoryRepository;
import com.treishvaam.financeapi.service.BlogPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class SitemapController {

    @Autowired
    private BlogPostService blogPostService;
    
    // NEWLY ADDED: To get category slugs
    @Autowired
    private CategoryRepository categoryRepository;

    private static final String BASE_URL = "https://treishfin.treishvaamgroup.com";

    private static final List<String> STATIC_PAGES = List.of(
            "/",
            "/about",
            "/vision",
            "/contact",
            "/blog"
    );

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getSitemap() {
        StringBuilder sitemap = new StringBuilder();
        sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (String page : STATIC_PAGES) {
            sitemap.append(createUrlEntry(BASE_URL + page, "daily", "1.0", null));
        }

        List<BlogPost> publishedPosts = blogPostService.findAllByStatus(PostStatus.PUBLISHED);
        
        // Create a map of category names to category slugs for efficient lookup
        Map<String, String> categorySlugMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getName, Category::getSlug, (slug1, slug2) -> slug1));

        for (BlogPost post : publishedPosts) {
            // Skip posts if they don't have the necessary components for the new URL
            if (post.getUserFriendlySlug() == null || post.getUrlArticleId() == null || post.getCategory() == null) {
                continue;
            }

            String categorySlug = categorySlugMap.getOrDefault(post.getCategory(), "uncategorized");
            
            // Construct the new, correct URL
            String postUrl = String.format("%s/blog/category/%s/%s/%s",
                    BASE_URL,
                    categorySlug,
                    post.getUserFriendlySlug(),
                    post.getUrlArticleId()
            );

            String lastMod = null;
            if (post.getUpdatedAt() != null) {
                lastMod = post.getUpdatedAt()
                              .atZone(ZoneId.of("UTC"))
                              .toLocalDate()
                              .format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            sitemap.append(createUrlEntry(postUrl, "weekly", "0.9", lastMod));
        }

        sitemap.append("</urlset>");

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);

        return new ResponseEntity<>(sitemap.toString(), headers, HttpStatus.OK);
    }

    private String createUrlEntry(String loc, String changefreq, String priority, String lastmod) {
        StringBuilder urlEntry = new StringBuilder();
        urlEntry.append("  <url>\n");
        urlEntry.append("    <loc>").append(loc).append("</loc>\n");
        if (lastmod != null && !lastmod.isEmpty()) {
            urlEntry.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        }
        urlEntry.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        urlEntry.append("    <priority>").append(priority).append("</priority>\n");
        urlEntry.append("  </url>\n");
        return urlEntry.toString();
    }
}