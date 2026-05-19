package com.treishvaam.financeapi.marketdata;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Consumer for asynchronous market data update events.
 *
 * <p>Scope: - Listens to RabbitMQ queues to decouple heavy Python execution from Tomcat threads.
 *
 * <p>Critical Dependencies: - MarketDataService.
 *
 * <p>Security Constraints: - Ensure proper error handling to prevent poison pill messages blocking
 * the queue.
 *
 * <p>Non-Negotiables: - Must operate off the main thread.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED (Phase 5 - ARCH-03): • Created
 * MarketUpdateConsumer to listen for `MARKET_UPDATE` events. • Why: Resolves system stall during
 * heavy Python ProcessBuilder execution.
 */
import com.treishvaam.finance.messaging.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MarketUpdateConsumer {

  private static final Logger logger = LoggerFactory.getLogger(MarketUpdateConsumer.class);

  @Autowired private MarketDataService marketDataService;

  @RabbitListener(queues = "${rabbitmq.queue.market-update:internal.queue}")
  public void consumeMarketUpdate(EventMessage message) {
    if ("MARKET_UPDATE".equals(message.getEventType())) {
      logger.info("Received market update event from source: {}", message.getSource());
      try {
        marketDataService.runPythonHistoryAndQuoteUpdate(message.getSource());
      } catch (Exception e) {
        logger.error("Failed to process market update event.", e);
        // Allow DLX routing if configured
        throw e;
      }
    }
  }
}
