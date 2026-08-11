package com.treishvaam.finance.messaging;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Centralized asynchronous event publisher bridging Tomcat threads with RabbitMQ
 * queues.
 *
 * <p>Scope: - Emits loosely-coupled events for Search Indexing, Sitemap Generation, Market Data
 * Updates, and Video Transcoding.
 *
 * <p>Critical Dependencies: - Backend: RabbitMQ Container, `RabbitMQConfig.java`.
 *
 * <p>Security Constraints: - Network transport natively secured within the internal Docker
 * `treish_net` bridge.
 *
 * <p>Change Intent: - Enabled HLS Transcoding event dispatches to trigger the Alpine daemon.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Phase 3 - Enterprise Video Pipeline): •
 * Injected `publishVideoTranscodeEvent(Long videoId)`. • Why: Hands off the compute-heavy FFmpeg
 * workload to the `treishvaam-transcoder` daemon safely and instantly, freeing up the HTTP thread
 * for the frontend user.
 */
import com.treishvaam.financeapi.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MessagePublisher {

    @Autowired private RabbitTemplate template;

    @Value("${rabbitmq.queue.market-update:internal.queue}")
    private String marketUpdateQueue;

    public void publishSearchIndexEvent(Long postId, String action) {
        EventMessage message = new EventMessage(action, postId, "Search Index Update");
        template.convertAndSend(
                RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_SEARCH, message);
        System.out.println(" [x] Published Search Event: " + action + " for Post ID: " + postId);
    }

    public void publishSitemapRegenerateEvent() {
        EventMessage message = new EventMessage("REGENERATE", 0L, "Sitemap Refresh");
        template.convertAndSend(
                RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_SITEMAP, message);
        System.out.println(" [x] Published Sitemap Regeneration Event");
    }

    // ARCH-03 addition for Market Updates
    public void publish(EventMessage message) {
        template.convertAndSend(marketUpdateQueue, message);
    }

    // Phase 3 Enterprise Video Pipeline
    public void publishVideoTranscodeEvent(Long videoId) {
        String payload = "{\"videoId\":\"" + videoId + "\"}";
        template.convertAndSend("video.transcode.queue", payload);
        System.out.println(" [x] Published Video Transcode Event for Post ID: " + videoId);
    }
}
