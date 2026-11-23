/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Service for publishing BlogPost articles to LinkedIn (UGC Shares API).
 *
 * <p>Scope: - Builds the canonical public article URL and posts a LinkedIn share message.
 *
 * <p>Critical Dependencies: - Backend: BlogPost model, WebClient (api.linkedin.com v2).
 *
 * <p>Security Constraints: - LinkedIn API credentials MUST be env/property driven (never
 * hardcoded); canonical post URLs must match SitemapService.constructBlogPostUrl format (VULN-30).
 *
 * <p>Non-Negotiables: - The public article URL is always property-driven ({@code
 * app.finance.domain}, default https://treishvaamfinance.com); the legacy hardcoded domain is
 * banned (VULN-30).
 *
 * <p>Change Intent: - VULN-30: Remove the hardcoded legacy domain; use property-driven {@code
 * financeDomain} + canonical {@code /category/{catSlug}/{userFriendlySlug}/{urlArticleId}} URL
 * shape, matching the sitemap.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Batch 2B - P2.9, VULN-30): • Created
 * LinkedInService with canonical-URL construction.
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section acts as the institutional memory
 * for future AI sessions. It must never be deleted, truncated, rewritten, or regenerated. Future AI
 * must append only.
 */
package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class LinkedInService {

    private final WebClient webClient;

    // Canonical public domain — property-driven (VULN-30). Never hardcode business domains.
    @Value("${app.finance.domain:https://treishvaamfinance.com}")
    private String financeDomain;

    // MODIFICATION: Added ':' to make properties optional.
    // This prevents the application from crashing if the values are not in the properties file.
    @Value("${linkedin.api.accessToken:}")
    private String accessToken;

    @Value("${linkedin.api.authorUrn:}")
    private String authorUrn;

    public LinkedInService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.linkedin.com/v2").build();
    }

    /**
     * Canonical public article URL, matching SitemapService.constructBlogPostUrl:
     * {financeDomain}/category/{catSlug}/{userFriendlySlug}/{urlArticleId} with graceful fallbacks
     * when optional fields are null.
     */
    public String buildCanonicalPostUrl(BlogPost post) {
        String catSlug =
                (post.getCategory() != null && post.getCategory().getSlug() != null)
                        ? post.getCategory().getSlug()
                        : "general";
        String userSlug =
                post.getUserFriendlySlug() != null
                        ? post.getUserFriendlySlug()
                        : (post.getSlug() != null ? post.getSlug() : String.valueOf(post.getId()));
        String articleId =
                post.getUrlArticleId() != null
                        ? post.getUrlArticleId()
                        : (post.getSlug() != null ? post.getSlug() : String.valueOf(post.getId()));
        return String.format("%s/category/%s/%s/%s", financeDomain, catSlug, userSlug, articleId);
    }

    public Mono<String> sharePost(
            BlogPost post, String customMessage, java.util.List<String> tags) {
        // MODIFICATION: Added a check to ensure configuration is present.
        // If properties are missing, the feature is disabled and will not proceed.
        if (accessToken == null
                || accessToken.isEmpty()
                || authorUrn == null
                || authorUrn.isEmpty()) {
            String errorMessage = "LinkedIn sharing is disabled due to missing API configuration.";
            System.err.println(errorMessage);
            return Mono.just(errorMessage);
        }

        // VULN-30: canonical public article URL (was legacy hardcoded domain, VULN-30)
        String postUrl = buildCanonicalPostUrl(post);

        StringBuilder textBuilder = new StringBuilder();
        if (customMessage != null && !customMessage.isEmpty()) {
            textBuilder.append(customMessage);
        } else {
            textBuilder.append("Check out this new article: ").append(post.getTitle());
        }
        textBuilder.append("\n\n").append(postUrl);

        if (tags != null && !tags.isEmpty()) {
            textBuilder.append("\n\n");
            String hashtags =
                    tags.stream()
                            .map(tag -> "#" + tag.replaceAll("\\s+", ""))
                            .collect(Collectors.joining(" "));
            textBuilder.append(hashtags);
        }

        Map<String, Object> specificContent = new HashMap<>();
        Map<String, Object> shareContent = new HashMap<>();
        shareContent.put("shareCommentary", Map.of("text", textBuilder.toString()));
        shareContent.put("shareMediaCategory", "ARTICLE");

        Map<String, Object> media = new HashMap<>();
        media.put("status", "READY");
        media.put("originalUrl", postUrl);
        media.put("title", Map.of("text", post.getTitle()));

        java.util.List<Map<String, Object>> mediaList = new java.util.ArrayList<>();
        mediaList.add(media);

        shareContent.put("media", mediaList);
        specificContent.put("com.linkedin.ugc.ShareContent", shareContent);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("author", authorUrn);
        requestBody.put("lifecycleState", "PUBLISHED");
        requestBody.put("specificContent", specificContent);
        requestBody.put("visibility", Map.of("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"));

        return this.webClient
                .post()
                .uri("/ugcPosts")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("X-Restli-Protocol-Version", "2.0.0")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(
                        response ->
                                System.out.println("Successfully shared on LinkedIn: " + response))
                .doOnError(
                        error ->
                                System.err.println(
                                        "Failed to share on LinkedIn: " + error.getMessage()));
    }
}
