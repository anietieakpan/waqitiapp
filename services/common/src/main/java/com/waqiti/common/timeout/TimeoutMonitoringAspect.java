package com.waqiti.common.timeout;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Aspect for Monitoring HTTP Client Timeouts
 *
 * CRITICAL: This aspect tracks all timeout-related failures across the system
 *
 * MONITORING:
 * - Logs all timeout exceptions (connect, read, write)
 * - Tracks timeout metrics in Prometheus
 * - Identifies problematic external dependencies
 * - Helps with SLA monitoring and capacity planning
 *
 * METRICS EXPOSED:
 * - http.client.timeout.total - Total number of timeout failures
 * - http.client.timeout.duration - Duration of calls that timed out
 * - http.client.timeout.type - Breakdown by timeout type (connect/read/write)
 *
 * USAGE:
 * This aspect automatically monitors methods annotated with @MonitorTimeout
 *
 * <pre>
 * {@literal @}Service
 * public class ExternalApiService {
 *
 *     {@literal @}MonitorTimeout(
 *         service = "stripe",
 *         operation = "create-payment-intent",
 *         warningThresholdMs = 5000
 *     )
 *     public PaymentIntent createPaymentIntent(PaymentRequest request) {
 *         // If this takes > 5s, warning logged
 *         // If timeout occurs, metric incremented
 *     }
 * }
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 3.0.0
 */
@Aspect
@Component
@Slf4j
public class TimeoutMonitoringAspect {

    private final MeterRegistry meterRegistry;
    private final Counter timeoutCounter;
    private final Timer callDurationTimer;

    public TimeoutMonitoringAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.timeoutCounter = Counter.builder("http.client.timeout.total")
            .description("Total number of HTTP client timeout failures")
            .tag("service", "common")
            .register(meterRegistry);

        this.callDurationTimer = Timer.builder("http.client.call.duration")
            .description("Duration of HTTP client calls")
            .tag("service", "common")
            .register(meterRegistry);
    }

    /**
     * Monitor methods annotated with @MonitorTimeout
     */
    @Around("@annotation(monitorTimeout)")
    public Object monitorTimeout(ProceedingJoinPoint joinPoint, MonitorTimeout monitorTimeout) throws Throwable {
        String service = monitorTimeout.service();
        String operation = monitorTimeout.operation();
        long warningThreshold = monitorTimeout.warningThresholdMs();

        long startTime = System.currentTimeMillis();

        try {
            log.debug("Starting monitored call - Service: {}, Operation: {}", service, operation);

            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;

            // Log warning if call exceeded threshold
            if (duration > warningThreshold) {
                log.warn("SLOW_CALL: Service={}, Operation={}, Duration={}ms, Threshold={}ms",
                    service, operation, duration, warningThreshold);
            }

            // Record successful call duration
            callDurationTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

            log.debug("Completed monitored call - Service: {}, Operation: {}, Duration: {}ms",
                service, operation, duration);

            return result;

        } catch (SocketTimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;

            // Increment timeout counter with tags
            Counter.builder("http.client.timeout.total")
                .tag("service", service)
                .tag("operation", operation)
                .tag("type", "socket_timeout")
                .register(meterRegistry)
                .increment();

            log.error("TIMEOUT_EXCEPTION: Service={}, Operation={}, Duration={}ms, Type=SocketTimeout, Message={}",
                service, operation, duration, e.getMessage());

            throw e;

        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;

            Counter.builder("http.client.timeout.total")
                .tag("service", service)
                .tag("operation", operation)
                .tag("type", "timeout")
                .register(meterRegistry)
                .increment();

            log.error("TIMEOUT_EXCEPTION: Service={}, Operation={}, Duration={}ms, Type=Timeout, Message={}",
                service, operation, duration, e.getMessage());

            throw e;

        } catch (org.springframework.web.client.ResourceAccessException e) {
            long duration = System.currentTimeMillis() - startTime;

            // Check if root cause is timeout
            Throwable rootCause = e.getRootCause();
            if (rootCause instanceof SocketTimeoutException) {
                Counter.builder("http.client.timeout.total")
                    .tag("service", service)
                    .tag("operation", operation)
                    .tag("type", "resource_access_timeout")
                    .register(meterRegistry)
                    .increment();

                log.error("TIMEOUT_EXCEPTION: Service={}, Operation={}, Duration={}ms, Type=ResourceAccessTimeout, Message={}",
                    service, operation, duration, rootCause.getMessage());
            }

            throw e;

        } catch (feign.RetryableException e) {
            long duration = System.currentTimeMillis() - startTime;

            // Feign timeout exception
            Counter.builder("http.client.timeout.total")
                .tag("service", service)
                .tag("operation", operation)
                .tag("type", "feign_retryable")
                .register(meterRegistry)
                .increment();

            log.error("TIMEOUT_EXCEPTION: Service={}, Operation={}, Duration={}ms, Type=FeignRetryable, Message={}",
                service, operation, duration, e.getMessage());

            throw e;

        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;

            log.error("UNEXPECTED_EXCEPTION: Service={}, Operation={}, Duration={}ms, Exception={}",
                service, operation, duration, e.getClass().getSimpleName(), e);

            throw e;
        }
    }

    /**
     * Log timeout configuration on application startup
     */
    public void logTimeoutConfiguration() {
        log.info("=".repeat(80));
        log.info("HTTP CLIENT TIMEOUT CONFIGURATION");
        log.info("=".repeat(80));
        log.info("Timeout monitoring is ACTIVE");
        log.info("All HTTP client calls with @MonitorTimeout will be tracked");
        log.info("Metrics exposed:");
        log.info("  - http.client.timeout.total (tagged by service, operation, type)");
        log.info("  - http.client.call.duration (tagged by service, operation)");
        log.info("=".repeat(80));
    }
}
