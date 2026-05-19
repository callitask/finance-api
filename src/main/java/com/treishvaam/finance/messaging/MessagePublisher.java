package com.treishvaam.finance.messaging;

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
    template.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_SEARCH, message);
    System.out.println(" [x] Published Search Event: " + action + " for Post ID: " + postId);
  }

  public void publishSitemapRegenerateEvent() {
    EventMessage message = new EventMessage("REGENERATE", 0L, "Sitemap Refresh");
    template.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_SITEMAP, message);
    System.out.println(" [x] Published Sitemap Regeneration Event");
  }

  // ARCH-03 addition for Market Updates
  public void publish(EventMessage message) {
    template.convertAndSend(marketUpdateQueue, message);
  }
}
