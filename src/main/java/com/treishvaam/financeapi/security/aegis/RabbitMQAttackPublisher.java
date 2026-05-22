package com.treishvaam.financeapi.security.aegis;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Bridges real-time attack telemetry to the internal RabbitMQ event bus.
 *
 * <p>Scope: - Fires asynchronous events containing attacker IP, JA3, and payload signatures.
 *
 * <p>Critical Dependencies: - RabbitMQ: Requires the `aegis.threat.exchange` to be available. -
 * Wazuh Agent: Subscribes to these events downstream.
 *
 * <p>Security Constraints: - Fire-and-forget mechanism; must never block the main request thread.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (AEGIS Phase 2): • Initial creation. Wraps
 * threat telemetry in JSON maps for the AMQP broker.
 */
@Service
public class RabbitMQAttackPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQAttackPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private static final String EXCHANGE_NAME = "aegis.threat.exchange";
    private static final String ROUTING_KEY = "threat.detected";

    public RabbitMQAttackPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishAttackEvent(
            String ip, String ja3Fingerprint, String targetPath, String triggerReason) {
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("timestamp", Instant.now().toString());
            eventData.put("ip", ip != null ? ip : "UNKNOWN");
            eventData.put("ja3", ja3Fingerprint != null ? ja3Fingerprint : "UNKNOWN");
            eventData.put("target_path", targetPath);
            eventData.put("trigger", triggerReason);

            // Fire and forget
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, eventData);
            logger.debug("Published threat telemetry to message bus for IP: {}", ip);
        } catch (Exception e) {
            logger.error("Failed to publish AEGIS threat event to RabbitMQ", e);
        }
    }
}
