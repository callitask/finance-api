package com.treishvaam.financeapi.messaging;

import com.treishvaam.financeapi.config.RabbitMQConfig;
import com.treishvaam.financeapi.model.BlogPost;
import com.treishvaam.financeapi.repository.BlogPostRepository;
import com.treishvaam.financeapi.search.PostDocument;
import com.treishvaam.financeapi.search.PostSearchRepository;
import com.treishvaam.financeapi.service.SitemapService; // FIXED IMPORT
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MessageListener {

  private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

  @Autowired private PostSearchRepository postSearchRepository;

  @Autowired private BlogPostRepository blogPostRepository;

  // FIXED: Injected SitemapService (Dynamic) instead of GenerationService (File-based)
  @Autowired private SitemapService sitemapService;

  // WORKER 1: Handles Search Indexing (Elasticsearch)
  @RabbitListener(queues = RabbitMQConfig.QUEUE_SEARCH_INDEX)
  public void handleSearchIndexEvent(EventMessage message) {
    logger.info(" [Async] Processing Search Event: {}", message);

    if ("INDEX".equals(message.getEventType()) || "UPDATE".equals(message.getEventType())) {
      Optional<BlogPost> postOpt = blogPostRepository.findById(message.getEntityId());
      if (postOpt.isPresent()) {
        BlogPost post = postOpt.get();
        // Only index if published
        if (post.getStatus() == com.treishvaam.financeapi.model.PostStatus.PUBLISHED) {
          try {
            String categorySlug =
                post.getCategory() != null ? post.getCategory().getSlug() : "uncategorized";
            PostDocument doc =
                new PostDocument(
                    post.getId().toString(),
                    post.getTitle(),
                    post.getCustomSnippet(),
                    post.getSlug(),
                    post.getStatus().name(),
                    categorySlug,
                    post.getUserFriendlySlug(),
                    post.getUrlArticleId());
            postSearchRepository.save(doc);
            logger.info(" [Async] Successfully Indexed Post ID: {}", post.getId());
          } catch (Exception e) {
            logger.error("Failed to index post ID: " + post.getId(), e);
          }
        }
      }
    } else if ("DELETE".equals(message.getEventType())) {
      try {
        postSearchRepository.deleteById(message.getEntityId().toString());
        logger.info(" [Async] Deleted Post ID: {} from Index", message.getEntityId());
      } catch (Exception e) {
        logger.error("Failed to delete post index ID: " + message.getEntityId(), e);
      }
    }
  }

  // WORKER 2: Handles Sitemap Updates
  @RabbitListener(queues = RabbitMQConfig.QUEUE_SITEMAP)
  public void handleSitemapEvent(EventMessage message) {
    logger.info(" [Async] Received Sitemap update event. Clearing cache...");
    try {
      // ENTERPRISE FIX: Instead of generating files, we just clear the cache.
      // The next request to /sitemap.xml will automatically regenerate fresh data.
      sitemapService.clearCaches();
      logger.info(" [Async] Sitemap cache cleared. Data is fresh.");
    } catch (Exception e) {
      logger.error("Sitemap cache eviction failed", e);
    }
  }
}
