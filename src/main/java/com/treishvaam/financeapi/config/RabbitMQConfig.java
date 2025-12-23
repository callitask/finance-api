package com.treishvaam.financeapi.config;

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
