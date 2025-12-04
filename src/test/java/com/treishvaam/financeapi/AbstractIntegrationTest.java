package com.treishvaam.financeapi;

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

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    // Hardcode the Docker API version to ensure compatibility with your host
    static {
        System.setProperty("docker.client.api.version", "1.44");
    }

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:10.6")
            .withDatabaseName("finance_db")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:alpine"))
            .withExposedPorts(6379);

    // FIX: Optimized for 8GB Host
    // 1. Increased Heap to 1GB (was 256MB)
    // 2. Disabled heavy xPack features (ML, Monitoring) for faster boot
    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.17.10")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.monitoring.enabled", "false") // Disable monitoring
            .withEnv("xpack.ml.enabled", "false")         // Disable Machine Learning
            .withEnv("xpack.watcher.enabled", "false")    // Disable Watcher
            .withEnv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")     // Allocate 1GB RAM
            .withStartupTimeout(Duration.ofMinutes(3));   // Allow 3 mins for startup

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"));

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        // MariaDB Connection Details
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        
        // CRITICAL FIX: Override the H2 driver setting from application-test.properties
        // We are using real MariaDB now, so we must use the MariaDB driver.
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
        
        // Enable Liquibase for schema validation
        registry.add("spring.liquibase.enabled", () -> "true");
    }
}