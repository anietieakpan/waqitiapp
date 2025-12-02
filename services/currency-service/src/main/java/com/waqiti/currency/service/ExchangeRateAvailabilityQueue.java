package com.waqiti.currency.service;

import com.waqiti.currency.model.ExchangeRateAvailabilityRequest;
import com.waqiti.currency.model.Priority;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

/**
 * Exchange Rate Availability Queue
 *
 * Manages queued conversion requests waiting for exchange rate availability.
 * Features:
 * - Priority-based queuing
 * - Automatic retry scheduling
 * - Rate availability monitoring
 * - Queue metrics and monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateAvailabilityQueue {

    private final MeterRegistry meterRegistry;
    private final PriorityBlockingQueue<ExchangeRateAvailabilityRequest> queue =
            new PriorityBlockingQueue<>(1000, Comparator
                    .comparing(ExchangeRateAvailabilityRequest::getPriority)
                    .thenComparing(ExchangeRateAvailabilityRequest::getNextRetryTime));

    public ExchangeRateAvailabilityQueue(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Register queue size gauge
        Gauge.builder("currency.rate_availability_queue.size", queue, q -> q.size())
                .description("Number of conversion requests waiting for exchange rate")
                .register(meterRegistry);
    }

    /**
     * Add request to queue
     */
    public void add(ExchangeRateAvailabilityRequest request) {
        log.info("Adding conversion to rate availability queue: conversionId={} {}/{} retryCount={} correlationId={}",
                request.getConversionId(), request.getFromCurrency(), request.getToCurrency(),
                request.getRetryCount(), request.getCorrelationId());

        queue.offer(request);

        Counter.builder("currency.rate_availability_queue.added")
                .tag("fromCurrency", request.getFromCurrency())
                .tag("toCurrency", request.getToCurrency())
                .tag("priority", request.getPriority().name())
                .register(meterRegistry)
                .increment();

        log.debug("Rate availability queue size: {}", queue.size());
    }

    /**
     * Poll request from queue
     */
    public ExchangeRateAvailabilityRequest poll() {
        ExchangeRateAvailabilityRequest request = queue.poll();

        if (request != null) {
            Counter.builder("currency.rate_availability_queue.polled")
                    .tag("fromCurrency", request.getFromCurrency())
                    .tag("toCurrency", request.getToCurrency())
                    .register(meterRegistry)
                    .increment();
        }

        return request;
    }

    /**
     * Get request ready for retry
     */
    public ExchangeRateAvailabilityRequest getNextReadyRequest() {
        Instant now = Instant.now();

        return queue.stream()
                .filter(req -> req.getNextRetryTime().isBefore(now))
                .findFirst()
                .map(req -> {
                    queue.remove(req);
                    return req;
                })
                .orElse(null);
    }

    /**
     * Remove request from queue
     */
    public boolean remove(String conversionId) {
        boolean removed = queue.removeIf(req -> req.getConversionId().equals(conversionId));

        if (removed) {
            log.info("Removed conversion from rate availability queue: conversionId={}", conversionId);

            Counter.builder("currency.rate_availability_queue.removed")
                    .register(meterRegistry)
                    .increment();
        }

        return removed;
    }

    /**
     * Get queue size
     */
    public int size() {
        return queue.size();
    }

    /**
     * Check if queue contains conversion
     */
    public boolean contains(String conversionId) {
        return queue.stream()
                .anyMatch(req -> req.getConversionId().equals(conversionId));
    }

    /**
     * Get pending requests for currency pair
     */
    public long getPendingCountForPair(String fromCurrency, String toCurrency) {
        return queue.stream()
                .filter(req -> req.getFromCurrency().equals(fromCurrency) &&
                              req.getToCurrency().equals(toCurrency))
                .count();
    }

    /**
     * Scheduled cleanup of expired requests
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void cleanupExpiredRequests() {
        Instant cutoff = Instant.now().minusSeconds(86400); // 24 hours ago

        var expired = queue.stream()
                .filter(req -> req.getCreatedAt().isBefore(cutoff) ||
                              req.getRetryCount() >= req.getMaxRetries())
                .collect(Collectors.toList());

        if (!expired.isEmpty()) {
            log.warn("Removing {} expired requests from rate availability queue", expired.size());

            expired.forEach(req -> {
                queue.remove(req);

                Counter.builder("currency.rate_availability_queue.expired")
                        .tag("fromCurrency", req.getFromCurrency())
                        .tag("toCurrency", req.getToCurrency())
                        .register(meterRegistry)
                        .increment();
            });
        }
    }
}
