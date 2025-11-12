package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.model.PostStatus;
import com.treishvaam.financeapi.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SitemapGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(SitemapGenerationService.class);
    private static final int SITEMAP_PAGE_SIZE = 1000;
    private static final List<String> STATIC_PAGES = List.of("/", "/about", "/vision", "/contact");

    @Value("${storage.sitemap-dir}")
    private String sitemapDir;

    @Value("${app.base-url}")
    private String baseUrl;

    @Autowired
    private BlogPostService blogPostService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Scheduled(cron = "0 0 */6 * * *") // Runs every 6 hours
    public void generateSitemaps() {
        logger.info("Starting sitemap generation task...");
        try {
            Path sitemapPath = Paths.get(sitemapDir);
            Files.createDirectories(sitemapPath);

            generateStaticSitemap();
            generateCategoriesSitemap();

            long totalPosts = blogPostService.countPublishedPosts();
            int totalPostPages = (int) Math.ceil((double) totalPosts / SITEMAP_PAGE_SIZE);

            for (int i = 0; i < totalPostPages; i++) {
                generatePostsSitemap(i, SITEMAP_PAGE_SIZE);
            }

            generateSitemapIndex(totalPostPages);
            logger.info("Sitemap generation task finished successfully.");

        } catch (Exception e) {
            logger.error("Failed to generate sitemaps", e);
        }
    }

    private void generateSitemapIndex(int totalPostPages) throws IOException {
        StringBuilder index = new StringBuilder();
        index.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        index.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        index.append(createSitemapEntry(baseUrl + "/sitemaps/static.xml"));
        index.append(createSitemapEntry(baseUrl + "/sitemaps/categories.xml"));

        for (int i = 0; i < totalPostPages; i++) {
            index.append(createSitemapEntry(String.format("%s/sitemaps/posts-%d.xml", baseUrl, i + 1)));
        }

        index.append("</sitemapindex>");
        writeToFile("sitemap.xml", index.toString());
    }

    private void generateStaticSitemap() throws IOException {
        StringBuilder sitemap = new StringBuilder();
        sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (String page : STATIC_PAGES) {
            sitemap.append(createUrlEntry(baseUrl + page, null, "daily", "1.0"));
        }

        sitemap.append("</urlset>");
        writeToFile("static.xml", sitemap.toString());
    }

    private void generateCategoriesSitemap() throws IOException {
        StringBuilder sitemap = new StringBuilder();
        sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        List<Category> categories = categoryRepository.findAll();
        for (Category category : categories) {
            if (category.getSlug() == null) continue;
            String catUrl = String.format("%s/category/%s", baseUrl, category.getSlug());
            sitemap.append(createUrlEntry(catUrl, null, "weekly", "0.8"));
        }

        sitemap.append("</urlset>");
        writeToFile("categories.xml", sitemap.toString());
    }

    private void generatePostsSitemap(int page, int pageSize) throws IOException {
        StringBuilder sitemap = new StringBuilder();
        sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        Map<String, String> categorySlugMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getName, Category::getSlug, (slug1, slug2) -> slug1));

        Page<BlogPost> posts = blogPostService.findAllPublishedPosts(PageRequest.of(page, pageSize));
        
        for (BlogPost post : posts) {
            if (post.getUserFriendlySlug() == null || post.getUrlArticleId() == null || post.getCategory() == null) {
                continue;
            }

            String categorySlug = categorySlugMap.getOrDefault(post.getCategory().getName(), "uncategorized");
            
            String postUrl = String.format("%s/category/%s/%s/%s",
                    baseUrl,
                    categorySlug,
                    post.getUserFriendlySlug(),
                    post.getUrlArticleId()
            );

            String lastMod = formatLastMod(post.getUpdatedAt());
            sitemap.append(createUrlEntry(postUrl, lastMod, "weekly", "0.9"));
        }

        sitemap.append("</urlset>");
        writeToFile(String.format("posts-%d.xml", page + 1), sitemap.toString());
    }

    private String createSitemapEntry(String loc) {
        // A sitemap index doesn't need lastmod, but it could be added
        return String.format("  <sitemap>\n    <loc>%s</loc>\n  </sitemap>\n", loc);
    }

    private String createUrlEntry(String loc, String lastmod, String changefreq, String priority) {
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
    
    private String formatLastMod(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(ZoneId.of("UTC"))
                      .toLocalDate()
                      .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private void writeToFile(String fileName, String content) throws IOException {
        Path filePath = Paths.get(sitemapDir, fileName);
        Files.writeString(filePath, content);
        logger.debug("Generated sitemap file: {}", filePath);
    }
}