package com.waqiti.integration.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection for integration service operations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationMetrics {

    private final MeterRegistry meterRegistry;
    
    // User operation metrics
    private final Counter userCreationSuccessCounter;
    private final Counter userCreationFailureCounter;
    private final Timer userCreationTimer;
    
    // Account operation metrics
    private final Counter accountCreationSuccessCounter;
    private final Counter accountCreationFailureCounter;
    private final Timer accountCreationTimer;
    
    // Balance operation metrics
    private final Counter balanceRetrievalSuccessCounter;
    private final Counter balanceRetrievalFailureCounter;
    private final Timer balanceRetrievalTimer;
    
    // Payment operation metrics
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;
    private final Timer paymentTimer;
    
    // General metrics
    private final AtomicLong activeOperations = new AtomicLong(0);
    private final AtomicLong totalOperations = new AtomicLong(0);

    public IntegrationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // User operation counters
        this.userCreationSuccessCounter = Counter.builder("integration.user.creation.success")
                .description("Number of successful user creations")
                .register(meterRegistry);
                
        this.userCreationFailureCounter = Counter.builder("integration.user.creation.failure")
                .description("Number of failed user creations")
                .register(meterRegistry);
                
        this.userCreationTimer = Timer.builder("integration.user.creation.duration")
                .description("Duration of user creation operations")
                .register(meterRegistry);
        
        // Account operation counters
        this.accountCreationSuccessCounter = Counter.builder("integration.account.creation.success")
                .description("Number of successful account creations")
                .register(meterRegistry);
                
        this.accountCreationFailureCounter = Counter.builder("integration.account.creation.failure")
                .description("Number of failed account creations")
                .register(meterRegistry);
                
        this.accountCreationTimer = Timer.builder("integration.account.creation.duration")
                .description("Duration of account creation operations")
                .register(meterRegistry);
        
        // Balance operation counters
        this.balanceRetrievalSuccessCounter = Counter.builder("integration.balance.retrieval.success")
                .description("Number of successful balance retrievals")
                .register(meterRegistry);
                
        this.balanceRetrievalFailureCounter = Counter.builder("integration.balance.retrieval.failure")
                .description("Number of failed balance retrievals")
                .register(meterRegistry);
                
        this.balanceRetrievalTimer = Timer.builder("integration.balance.retrieval.duration")
                .description("Duration of balance retrieval operations")
                .register(meterRegistry);
        
        // Payment operation counters
        this.paymentSuccessCounter = Counter.builder("integration.payment.success")
                .description("Number of successful payments")
                .register(meterRegistry);
                
        this.paymentFailureCounter = Counter.builder("integration.payment.failure")
                .description("Number of failed payments")
                .register(meterRegistry);
                
        this.paymentTimer = Timer.builder("integration.payment.duration")
                .description("Duration of payment operations")
                .register(meterRegistry);
        
        // General gauges
        Gauge.builder("integration.operations.active", activeOperations, AtomicLong::get)
                .description("Number of active integration operations")
                .register(meterRegistry);
                
        Gauge.builder("integration.operations.total", totalOperations, AtomicLong::get)
                .description("Total number of integration operations")
                .register(meterRegistry);
    }

    /**
     * Record user creation metrics
     */
    public void recordUserCreation(long durationMs, boolean success) {
        if (success) {
            userCreationSuccessCounter.increment();
        } else {
            userCreationFailureCounter.increment();
        }
        userCreationTimer.record(Duration.ofMillis(durationMs));
        totalOperations.incrementAndGet();
        
        log.debug("Recorded user creation: duration={}ms, success={}", durationMs, success);
    }

    /**
     * Record account creation metrics
     */
    public void recordAccountCreation(long durationMs, boolean success) {
        if (success) {
            accountCreationSuccessCounter.increment();
        } else {
            accountCreationFailureCounter.increment();
        }
        accountCreationTimer.record(Duration.ofMillis(durationMs));
        totalOperations.incrementAndGet();
        
        log.debug("Recorded account creation: duration={}ms, success={}", durationMs, success);
    }

    /**
     * Record balance retrieval metrics
     */
    public void recordBalanceRetrieval(long durationMs, boolean success) {
        if (success) {
            balanceRetrievalSuccessCounter.increment();
        } else {
            balanceRetrievalFailureCounter.increment();
        }
        balanceRetrievalTimer.record(Duration.ofMillis(durationMs));
        totalOperations.incrementAndGet();
        
        log.debug("Recorded balance retrieval: duration={}ms, success={}", durationMs, success);
    }

    /**
     * Record payment metrics
     */
    public void recordPayment(long durationMs, BigDecimal amount, boolean success) {
        if (success) {
            paymentSuccessCounter.increment("amount_range", getAmountRange(amount));
        } else {
            paymentFailureCounter.increment("amount_range", getAmountRange(amount));
        }
        paymentTimer.record(Duration.ofMillis(durationMs), "amount_range", getAmountRange(amount));
        totalOperations.incrementAndGet();
        
        log.debug("Recorded payment: duration={}ms, amount={}, success={}", durationMs, amount, success);
    }

    /**
     * Start operation tracking
     */
    public void startOperation() {
        activeOperations.incrementAndGet();
    }

    /**
     * End operation tracking
     */
    public void endOperation() {
        activeOperations.decrementAndGet();
    }

    /**
     * Get current active operations count
     */
    public long getActiveOperations() {
        return activeOperations.get();
    }

    /**
     * Get total operations count
     */
    public long getTotalOperations() {
        return totalOperations.get();
    }

    /**
     * Get success rate for user operations
     */
    public double getUserCreationSuccessRate() {
        double total = userCreationSuccessCounter.count() + userCreationFailureCounter.count();
        return total > 0 ? userCreationSuccessCounter.count() / total : 0.0;
    }

    /**
     * Get success rate for account operations
     */
    public double getAccountCreationSuccessRate() {
        double total = accountCreationSuccessCounter.count() + accountCreationFailureCounter.count();
        return total > 0 ? accountCreationSuccessCounter.count() / total : 0.0;
    }

    /**
     * Get success rate for balance operations
     */
    public double getBalanceRetrievalSuccessRate() {
        double total = balanceRetrievalSuccessCounter.count() + balanceRetrievalFailureCounter.count();
        return total > 0 ? balanceRetrievalSuccessCounter.count() / total : 0.0;
    }

    /**
     * Get success rate for payment operations
     */
    public double getPaymentSuccessRate() {
        double total = paymentSuccessCounter.count() + paymentFailureCounter.count();
        return total > 0 ? paymentSuccessCounter.count() / total : 0.0;
    }

    private String getAmountRange(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(100)) <= 0) {
            return "small";
        } else if (amount.compareTo(BigDecimal.valueOf(1000)) <= 0) {
            return "medium";
        } else if (amount.compareTo(BigDecimal.valueOf(10000)) <= 0) {
            return "large";
        } else {
            return "very_large";
        }
    }
}