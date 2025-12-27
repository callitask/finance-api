package com.treishvaam.financeapi;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class FinanceApiApplicationTests extends AbstractIntegrationTest {

  @Test
  void contextLoads() {
    // This test ensures the Application Context loads
    // and can connect to all Testcontainers (DB, Redis, etc.)
  }
}
