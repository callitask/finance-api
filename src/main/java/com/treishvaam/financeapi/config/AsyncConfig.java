package com.treishvaam.financeapi.config;

import com.treishvaam.financeapi.config.tenant.TenantContext;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(25);
    executor.setThreadNamePrefix("Async-");

    // ENTERPRISE FIX: Decorate tasks to propagate Tenant ID to new threads
    executor.setTaskDecorator(new ContextCopyingDecorator());

    executor.initialize();
    return executor;
  }

  /** Copies the TenantContext and MDC (Logs) from the parent thread to the async thread. */
  static class ContextCopyingDecorator implements TaskDecorator {
    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
      // Capture context from the caller thread
      String tenantId = TenantContext.getTenantId();
      java.util.Map<String, String> mdcContext = MDC.getCopyOfContextMap();

      return () -> {
        try {
          // Restore context in the async thread
          if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
          }
          if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
          } else {
            MDC.clear();
          }
          runnable.run();
        } finally {
          // Clean up to prevent leakage in thread pool
          TenantContext.clear();
          MDC.clear();
        }
      };
    }
  }
}
