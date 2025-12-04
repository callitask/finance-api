package com.treishvaam.financeapi;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class FinanceApiApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // This test will now fail if the DB, Redis, Elastic, or RabbitMQ 
        // cannot be started or connected to. This is the ultimate "Sanity Check".
    }

}