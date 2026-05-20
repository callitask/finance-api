package com.treishvaam.financeapi.config;

/**
 * AI-CONTEXT:
 *
 * <p>Purpose: - Configures Spring AMQP RabbitMQ topology (Queues, Exchanges, Bindings).
 *
 * <p>Scope: - Declares internal.queue, search_index_queue, sitemap_queue, and DLX logic. - It must
 * never connect to external untrusted brokers.
 *
 * <p>Critical Dependencies: - Backend: MessagePublisher, MarketUpdateConsumer, MessageListener -
 * Frontend: N/A - Worker / SEO / Sitemap: Triggers sitemap cache evictions via sitemap_queue.
 *
 * <p>Security Constraints: - Credentials injected via environment variables. - Must enforce
 * dead-lettering for all durable queues.
 *
 * <p>Non-Negotiables: - All queues must be durable to survive broker restarts.
 *
 * <p>Change Intent: - Added internal.queue declaration to prevent 404 NOT FOUND crashes during
 * startup.
 *
 * <p>Future AI Guidance: - Do not remove DLX arguments.
 *
 * <p>IMMUTABLE CHANGE HISTORY (DO NOT DELETE): - ADDED: • `QUEUE_MARKET_UPDATE` constant and
 * `marketUpdateQueue()` Bean. • Why: The RabbitListener for market updates crashed the app on boot
 * because the queue did not exist. • Date: 2026-05-20 Phase 6 Observability/Stability Fix
 *
 * <p>- DO-NOT-DELETE RULE: This IMMUTABLE CHANGE HISTORY section must never be deleted, truncated,
 * rewritten, or regenerated. Future AI must append only.
 */
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  public static final String QUEUE_SEARCH_INDEX = "search_index_queue";
  public static final String QUEUE_SITEMAP = "sitemap_queue";
  public static final String QUEUE_MARKET_UPDATE = "internal.queue";

  // --- DLQ CONSTANTS ---
  public static final String QUEUE_DEAD_LETTER = "dead_letter_queue";
  public static final String EXCHANGE_DEAD_LETTER = "dead_letter_exchange";
  public static final String ROUTING_KEY_DEAD_LETTER = "dead.letter";

  public static final String EXCHANGE = "internal.exchange";
  public static final String ROUTING_KEY_SEARCH = "event.search";
  public static final String ROUTING_KEY_SITEMAP = "event.sitemap";

  // 1. Define Queues with DLQ Arguments
  @Bean
  public Queue searchIndexQueue() {
    return QueueBuilder.durable(QUEUE_SEARCH_INDEX)
        .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
        .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DEAD_LETTER)
        .build();
  }

  @Bean
  public Queue sitemapQueue() {
    return QueueBuilder.durable(QUEUE_SITEMAP)
        .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
        .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DEAD_LETTER)
        .build();
  }

  @Bean
  public Queue marketUpdateQueue() {
    return QueueBuilder.durable(QUEUE_MARKET_UPDATE)
        .withArgument("x-dead-letter-exchange", EXCHANGE_DEAD_LETTER)
        .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DEAD_LETTER)
        .build();
  }

  // 2. Define Dead Letter Queue
  @Bean
  public Queue deadLetterQueue() {
    return new Queue(QUEUE_DEAD_LETTER);
  }

  // 3. Define Exchanges
  @Bean
  public TopicExchange exchange() {
    return new TopicExchange(EXCHANGE);
  }

  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange(EXCHANGE_DEAD_LETTER);
  }

  // 4. Bind Queues to Exchange
  @Bean
  public Binding bindingSearch(Queue searchIndexQueue, TopicExchange exchange) {
    return BindingBuilder.bind(searchIndexQueue).to(exchange).with(ROUTING_KEY_SEARCH);
  }

  @Bean
  public Binding bindingSitemap(Queue sitemapQueue, TopicExchange exchange) {
    return BindingBuilder.bind(sitemapQueue).to(exchange).with(ROUTING_KEY_SITEMAP);
  }

  // Note: marketUpdateQueue is routed via default exchange by name, no explicit binding to
  // internal.exchange needed.

  // 5. Bind Dead Letter Queue to DLX
  @Bean
  public Binding bindingDeadLetter(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(deadLetterQueue)
        .to(deadLetterExchange)
        .with(ROUTING_KEY_DEAD_LETTER);
  }

  // 6. JSON Converter
  @Bean
  public MessageConverter converter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public AmqpTemplate template(ConnectionFactory connectionFactory) {
    final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(converter());
    return rabbitTemplate;
  }
}
