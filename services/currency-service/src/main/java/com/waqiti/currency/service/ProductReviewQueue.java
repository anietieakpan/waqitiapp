package com.waqiti.currency.service;

import com.waqiti.currency.model.ProductReviewRequest;
import com.waqiti.currency.model.Priority;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Product Review Queue
 *
 * Manages product review requests for currency-related features:
 * - Currency pair support requests
 * - Exchange rate provider evaluations
 * - Conversion limit adjustments
 * - Feature enhancement requests
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReviewQueue {

    private final MeterRegistry meterRegistry;
    private final PriorityBlockingQueue<ProductReviewRequest> queue =
            new PriorityBlockingQueue<>(1000, Comparator
                    .comparing(ProductReviewRequest::getPriority)
                    .thenComparing(ProductReviewRequest::getDemandCount, Comparator.reverseOrder()));

    private final Map<String, Integer> currencyPairDemand = new ConcurrentHashMap<>();

    public ProductReviewQueue(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Register queue size gauge
        Gauge.builder("currency.product_review_queue.size", queue, q -> q.size())
                .description("Number of product review requests pending")
                .register(meterRegistry);
    }

    /**
     * Add review request to queue
     */
    public void add(ProductReviewRequest request) {
        log.info("Adding product review request: type={} {}/{} priority={} correlationId={}",
                request.getReviewType(), request.getFromCurrency(), request.getToCurrency(),
                request.getPriority(), request.getCorrelationId());

        // Track currency pair demand
        if (request.getFromCurrency() != null && request.getToCurrency() != null) {
            String pair = request.getFromCurrency() + "/" + request.getToCurrency();
            int demand = currencyPairDemand.merge(pair, 1, Integer::sum);
            request.setDemandCount(demand);

            log.info("Currency pair demand: {} = {} requests", pair, demand);
        }

        queue.offer(request);

        Counter.builder("currency.product_review_queue.added")
                .tag("reviewType", request.getReviewType().name())
                .tag("priority", request.getPriority().name())
                .register(meterRegistry)
                .increment();

        log.debug("Product review queue size: {}", queue.size());
    }

    /**
     * Poll review request from queue
     */
    public ProductReviewRequest poll() {
        ProductReviewRequest request = queue.poll();

        if (request != null) {
            Counter.builder("currency.product_review_queue.polled")
                    .tag("reviewType", request.getReviewType().name())
                    .register(meterRegistry)
                    .increment();

            log.info("Polled product review request: type={} {}/{}",
                    request.getReviewType(), request.getFromCurrency(), request.getToCurrency());
        }

        return request;
    }

    /**
     * Get currency pair demand count
     */
    public int getDemandCount(String fromCurrency, String toCurrency) {
        String pair = fromCurrency + "/" + toCurrency;
        return currencyPairDemand.getOrDefault(pair, 0);
    }

    /**
     * Get queue size
     */
    public int size() {
        return queue.size();
    }

    /**
     * Check if queue contains review for currency pair
     */
    public boolean containsPair(String fromCurrency, String toCurrency) {
        return queue.stream()
                .anyMatch(req -> req.getFromCurrency().equals(fromCurrency) &&
                              req.getToCurrency().equals(toCurrency));
    }
}
