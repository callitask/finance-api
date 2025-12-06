package com.treishvaam.financeapi.messaging;

import com.treishvaam.financeapi.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessagePublisher {

  @Autowired private RabbitTemplate template;

  public void publishSearchIndexEvent(Long postId, String action) {
    EventMessage message = new EventMessage(action, postId, "Search Index Update");
    template.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_SEARCH, message);
    System.out.println(" [x] Published Search Event: " + action + " for Post ID: " + postId);
  }

  public void publishSitemapRegenerateEvent() {
    EventMessage message = new EventMessage("REGENERATE", 0L, "Sitemap Refresh");
    template.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_SITEMAP, message);
    System.out.println(" [x] Published Sitemap Regeneration Event");
  }
}
