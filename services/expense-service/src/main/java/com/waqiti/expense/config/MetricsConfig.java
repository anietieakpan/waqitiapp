package com.waqiti.expense.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Production-ready metrics configuration with custom business metrics
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags(
                "application", "expense-service",
                "service", "expense-tracking",
                "environment", System.getProperty("spring.profiles.active", "default")
        );
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Custom metrics for business operations
     */
    @Bean
    public ExpenseMetrics expenseMetrics(MeterRegistry registry) {
        return new ExpenseMetrics(registry);
    }

    /**
     * Business metrics collector
     */
    public static class ExpenseMetrics {
        private final MeterRegistry registry;

        public ExpenseMetrics(MeterRegistry registry) {
            this.registry = registry;
            log.info("Initialized custom expense metrics");
        }

        public void recordExpenseCreated(String currency, String category) {
            registry.counter("expense.created",
                    Tags.of("currency", currency, "category", category)).increment();
        }

        public void recordExpenseApproved(String category) {
            registry.counter("expense.approved",
                    Tags.of("category", category)).increment();
        }

        public void recordExpenseRejected(String category) {
            registry.counter("expense.rejected",
                    Tags.of("category", category)).increment();
        }

        public void recordBudgetExceeded(String budgetName) {
            registry.counter("budget.exceeded",
                    Tags.of("budget", budgetName)).increment();
        }

        public void recordReceiptUploaded(String fileType) {
            registry.counter("receipt.uploaded",
                    Tags.of("fileType", fileType)).increment();
        }

        public void recordExpenseAmount(double amount, String currency) {
            registry.summary("expense.amount",
                    Tags.of("currency", currency)).record(amount);
        }
    }
}
