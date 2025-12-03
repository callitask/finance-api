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
    public static final String EXCHANGE = "internal.exchange";
    public static final String ROUTING_KEY_SEARCH = "event.search";
    public static final String ROUTING_KEY_SITEMAP = "event.sitemap";

    // 1. Define Queues
    @Bean
    public Queue searchIndexQueue() {
        return new Queue(QUEUE_SEARCH_INDEX);
    }

    @Bean
    public Queue sitemapQueue() {
        return new Queue(QUEUE_SITEMAP);
    }

    // 2. Define Topic Exchange (The Hub)
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    // 3. Bind Queues to Exchange
    @Bean
    public Binding bindingSearch(Queue searchIndexQueue, TopicExchange exchange) {
        return BindingBuilder.bind(searchIndexQueue).to(exchange).with(ROUTING_KEY_SEARCH);
    }

    @Bean
    public Binding bindingSitemap(Queue sitemapQueue, TopicExchange exchange) {
        return BindingBuilder.bind(sitemapQueue).to(exchange).with(ROUTING_KEY_SITEMAP);
    }

    // 4. JSON Converter (So messages are readable JSON, not binary blobs)
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