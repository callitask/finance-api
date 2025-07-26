package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.service.BlogPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SitemapController {

    @Autowired
    private BlogPostService blogPostService;

    private static final String BASE_URL = "https://treishfin.treishvaamgroup.com";

    @GetMapping(value = "/sitemap.xml")
    public ResponseEntity<String> getSitemap() {
        List<BlogPost> posts = blogPostService.findAll();
        StringBuilder sitemap = new StringBuilder();

        sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Add static pages
        sitemap.append(createUrlEntry(BASE_URL, "daily", "1.0"));
        sitemap.append(createUrlEntry(BASE_URL + "/blog", "daily", "0.9"));

        // Add each blog post URL
        for (BlogPost post : posts) {
            String postUrl = BASE_URL + "/blog/" + post.getId();
            String lastMod = (post.getUpdatedAt() != null) ? post.getUpdatedAt().toString().substring(0, 10) : null;
            sitemap.append(createUrlEntry(postUrl, "weekly", "0.8", lastMod));
        }

        sitemap.append("</urlset>");

        // Manually set the Content-Type header to ensure it's treated as XML
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);

        return new ResponseEntity<>(sitemap.toString(), headers, HttpStatus.OK);
    }

    private String createUrlEntry(String loc, String changefreq, String priority) {
        return createUrlEntry(loc, changefreq, priority, null);
    }

    private String createUrlEntry(String loc, String changefreq, String priority, String lastmod) {
        StringBuilder urlEntry = new StringBuilder();
        urlEntry.append("  <url>\n");
        urlEntry.append("    <loc>").append(loc).append("</loc>\n");
        if (lastmod != null) {
            urlEntry.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        }
        urlEntry.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        urlEntry.append("    <priority>").append(priority).append("</priority>\n");
        urlEntry.append("  </url>\n");
        return urlEntry.toString();
    }
}