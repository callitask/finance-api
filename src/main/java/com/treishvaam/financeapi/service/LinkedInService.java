package com.treishvaam.financeapi.service;

import com.treishvaam.financeapi.model.BlogPost;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LinkedInService {

    private final WebClient webClient;

    // MODIFICATION: Added ':' to make properties optional.
    // This prevents the application from crashing if the values are not in the properties file.
    @Value("${linkedin.api.accessToken:}")
    private String accessToken;

    @Value("${linkedin.api.authorUrn:}")
    private String authorUrn;

    public LinkedInService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.linkedin.com/v2").build();
    }

    public Mono<String> sharePost(BlogPost post, String customMessage, java.util.List<String> tags) {
        // MODIFICATION: Added a check to ensure configuration is present.
        // If properties are missing, the feature is disabled and will not proceed.
        if (accessToken == null || accessToken.isEmpty() || authorUrn == null || authorUrn.isEmpty()) {
            String errorMessage = "LinkedIn sharing is disabled due to missing API configuration.";
            System.err.println(errorMessage);
            return Mono.just(errorMessage);
        }

        String postUrl = "https://treishfin.treishvaamgroup.com/blog/" + post.getId();

        StringBuilder textBuilder = new StringBuilder();
        if (customMessage != null && !customMessage.isEmpty()) {
            textBuilder.append(customMessage);
        } else {
            textBuilder.append("Check out this new article: ").append(post.getTitle());
        }
        textBuilder.append("\n\n").append(postUrl);

        if (tags != null && !tags.isEmpty()) {
            textBuilder.append("\n\n");
            String hashtags = tags.stream().map(tag -> "#" + tag.replaceAll("\\s+", "")).collect(Collectors.joining(" "));
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

        return this.webClient.post()
                .uri("/ugcPosts")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("X-Restli-Protocol-Version", "2.0.0")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> System.out.println("Successfully shared on LinkedIn: " + response))
                .doOnError(error -> System.err.println("Failed to share on LinkedIn: " + error.getMessage()));
    }
}