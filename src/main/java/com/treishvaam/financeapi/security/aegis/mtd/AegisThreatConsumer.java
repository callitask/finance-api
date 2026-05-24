package com.treishvaam.financeapi.security.aegis.mtd;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Executes AEGIS Phase 4 (Moving Target Defense) internal messaging integration. -
 * Completes the zero-trust feedback loop by consuming threat telemetry and initiating edge blocks.
 *
 * <p>Scope: - Subscribes to the `aegis.threat.exchange`. - Offloads Cloudflare Edge KV syncing to
 * background Virtual Threads to ensure 0ms impact on Tomcat HTTP workers.
 *
 * <p>Critical Dependencies: - RabbitMQ - CloudflareEdgeSyncService
 *
 * <p>Security Constraints: - Must fail gracefully if Cloudflare API is unreachable (do not crash
 * the consumer).
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (AEGIS Phase 4): • Initial creation. • Bound
 * `aegis.threat.queue` to `aegis.threat.exchange` with `threat.detected` routing key. • Wired to
 * `CloudflareEdgeSyncService`.
 */
@Component
public class AegisThreatConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AegisThreatConsumer.class);
    private final CloudflareEdgeSyncService cloudflareEdgeSyncService;

    public AegisThreatConsumer(CloudflareEdgeSyncService cloudflareEdgeSyncService) {
        this.cloudflareEdgeSyncService = cloudflareEdgeSyncService;
    }

    @RabbitListener(
            bindings =
                    @QueueBinding(
                            value = @Queue(value = "aegis.threat.queue", durable = "true"),
                            exchange =
                                    @Exchange(
                                            value = "aegis.threat.exchange",
                                            type = ExchangeTypes.DIRECT),
                            key = "threat.detected"))
    public void handleThreatEvent(Map<String, Object> eventData) {
        try {
            String ip = (String) eventData.getOrDefault("ip", "UNKNOWN");
            String ja3 = (String) eventData.getOrDefault("ja3", "UNKNOWN");
            String trigger = (String) eventData.getOrDefault("trigger", "aegis_consensus_block");

            logger.warn(
                    "AEGIS Consumer: Received threat event for IP [{}], JA3 [{}]. Initiating Edge Sync.",
                    ip,
                    ja3);

            cloudflareEdgeSyncService.blockAttackerAtEdge(ip, ja3, trigger);
        } catch (Exception e) {
            logger.error("Failed to process AEGIS threat event in consumer", e);
        }
    }
}
