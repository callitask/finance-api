package com.treishvaam.financeapi;

import java.time.Duration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

  // Hardcode the Docker API version to ensure compatibility with your host
  static {
    System.setProperty("docker.client.api.version", "1.44");
  }

  @Container
  static MariaDBContainer<?> mariadb =
      new MariaDBContainer<>("mariadb:10.6")
          .withDatabaseName("finance_db")
          .withUsername("testuser")
          .withPassword("testpass");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:alpine")).withExposedPorts(6379);

  // Memory Optimized Elasticsearch
  @Container
  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.17.10")
          .withEnv("discovery.type", "single-node")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("xpack.monitoring.enabled", "false")
          .withEnv("xpack.ml.enabled", "false")
          .withEnv("xpack.watcher.enabled", "false")
          .withEnv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")
          .withStartupTimeout(Duration.ofMinutes(3));

  @Container
  static RabbitMQContainer rabbitmq =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"));

  // --- FIX 1: Add MinIO Container for File Storage Tests ---
  @Container
  static GenericContainer<?> minio =
      new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
          .withExposedPorts(9000)
          .withEnv("MINIO_ROOT_USER", "test-access-key")
          .withEnv("MINIO_ROOT_PASSWORD", "test-secret-key")
          .withCommand("server /data");

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    // MariaDB
    registry.add("spring.datasource.url", mariadb::getJdbcUrl);
    registry.add("spring.datasource.username", mariadb::getUsername);
    registry.add("spring.datasource.password", mariadb::getPassword);

    // Force MariaDB Driver
    registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MariaDBDialect");

    // Redis
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

    // Elasticsearch
    registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);

    // RabbitMQ
    registry.add("spring.rabbitmq.host", rabbitmq::getHost);
    registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
    registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);

    // --- FIX 2: Configure MinIO connection ---
    registry.add(
        "storage.s3.endpoint", () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
    registry.add("storage.s3.access-key", () -> "test-access-key");
    registry.add("storage.s3.secret-key", () -> "test-secret-key");
    registry.add("storage.s3.bucket", () -> "test-bucket");
    registry.add("storage.s3.region", () -> "us-east-1");

    // --- FIX 3: Point Liquibase to the XML file (Not YAML) ---
    registry.add("spring.liquibase.enabled", () -> "true");
    registry.add(
        "spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.xml");
  }
}
