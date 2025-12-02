package com.waqiti.billingorchestrator.service;

import com.waqiti.billingorchestrator.dto.request.RecordUsageRequest;
import com.waqiti.billingorchestrator.dto.response.UsageRecordResponse;
import com.waqiti.billingorchestrator.dto.response.UsageSummaryResponse;
import com.waqiti.billingorchestrator.entity.UsageRecord;
import com.waqiti.billingorchestrator.entity.UsageRecord.UsageStatus;
import com.waqiti.billingorchestrator.repository.UsageRecordRepository;
import com.waqiti.common.alerting.AlertingService;
import com.waqiti.common.idempotency.Idempotent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Usage Tracking Service
 *
 * Handles consumption-based billing for metered services.
 *
 * CRITICAL BUSINESS FUNCTIONS:
 * - Record usage in real-time (API calls, storage, bandwidth, etc.)
 * - Aggregate usage for billing periods
 * - Calculate usage charges
 * - Detect usage anomalies (spike detection)
 * - Support tiered pricing models
 *
 * IDEMPOTENCY GUARANTEE:
 * - All usage recording is idempotent via idempotencyKey
 * - Prevents duplicate charges from retries
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UsageTrackingService {

    private final UsageRecordRepository usageRecordRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final AlertingService alertingService;

    // Metrics
    private final Counter usageRecorded;
    private final Counter duplicateUsage;
    private final Counter usageAnomalies;

    public UsageTrackingService(
            UsageRecordRepository usageRecordRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            AlertingService alertingService) {
        this.usageRecordRepository = usageRecordRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.alertingService = alertingService;

        // Initialize metrics
        this.usageRecorded = Counter.builder("billing.usage.recorded")
                .description("Total usage records created")
                .register(meterRegistry);
        this.duplicateUsage = Counter.builder("billing.usage.duplicate")
                .description("Duplicate usage records rejected (idempotency)")
                .register(meterRegistry);
        this.usageAnomalies = Counter.builder("billing.usage.anomalies")
                .description("Usage anomalies detected (spike)")
                .register(meterRegistry);
    }

    /**
     * Records usage for consumption-based billing
     *
     * IDEMPOTENT: Uses idempotencyKey to prevent duplicate charges
     */
    @Transactional
    @Idempotent(
        keyExpression = "'usage-record:' + #request.idempotencyKey",
        serviceName = "billing-orchestrator-service",
        operationType = "RECORD_USAGE",
        userIdExpression = "#request.accountId",
        ttlHours = 72
    )
    @CircuitBreaker(name = "usage-tracking-service", fallbackMethod = "recordUsageFallback")
    public UsageRecordResponse recordUsage(RecordUsageRequest request) {
        log.info("Recording usage for account: {}, metric: {}, quantity: {}",
                request.getAccountId(), request.getMetricName(), request.getQuantity());

        // Check for duplicate (idempotency)
        if (request.getIdempotencyKey() != null &&
            usageRecordRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {

            log.warn("Duplicate usage record detected: {}", request.getIdempotencyKey());
            duplicateUsage.increment();

            UsageRecord existing = usageRecordRepository
                    .findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow();

            return mapToResponse(existing);
        }

        // Calculate pricing
        BigDecimal unitPrice = request.getUnitPrice() != null ?
                request.getUnitPrice() : lookupUnitPrice(request.getMetricName());

        BigDecimal totalAmount = request.getQuantity().multiply(unitPrice);

        // Create usage record
        UsageRecord usageRecord = UsageRecord.builder()
                .accountId(request.getAccountId())
                .customerId(request.getCustomerId())
                .subscriptionId(request.getSubscriptionId())
                .metricName(request.getMetricName())
                .metricCategory(determineMetricCategory(request.getMetricName()))
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .usageTimestamp(request.getUsageTimestamp() != null ?
                        request.getUsageTimestamp() : LocalDateTime.now())
                .status(UsageStatus.RECORDED)
                .billed(false)
                .idempotencyKey(request.getIdempotencyKey())
                .source(request.getSource())
                .resourceId(request.getResourceId())
                .tags(request.getTags())
                .build();

        usageRecord = usageRecordRepository.save(usageRecord);

        // Increment metrics
        usageRecorded.increment();

        // Detect usage anomalies (spike detection)
        detectUsageAnomaly(request.getAccountId(), request.getMetricName(), request.getQuantity());

        // Publish usage event
        publishUsageEvent(usageRecord);

        log.info("Usage recorded successfully: {}, amount: {}", usageRecord.getId(), totalAmount);

        return mapToResponse(usageRecord);
    }

    /**
     * Get usage summary for billing period
     */
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "usage-tracking-service")
    public UsageSummaryResponse getUsageSummary(UUID accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching usage summary for account: {} from {} to {}", accountId, startDate, endDate);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        // Get all usage records for period
        List<UsageRecord> usageRecords = usageRecordRepository
                .findByAccountAndPeriod(accountId, start, end);

        // Aggregate by metric
        Map<String, UsageMetricSummary> usageByMetric = usageRecords.stream()
                .collect(Collectors.groupingBy(
                        UsageRecord::getMetricName,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                records -> UsageMetricSummary.builder()
                                        .metricName(records.get(0).getMetricName())
                                        .totalQuantity(records.stream()
                                                .map(UsageRecord::getQuantity)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                                        .totalAmount(records.stream()
                                                .map(UsageRecord::getTotalAmount)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                                        .recordCount(records.size())
                                        .unit(records.get(0).getUnit())
                                        .build()
                        )
                ));

        // Calculate totals
        BigDecimal totalAmount = usageRecords.stream()
                .map(UsageRecord::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long unbilledCount = usageRecords.stream()
                .filter(u -> Boolean.FALSE.equals(u.getBilled()))
                .count();

        log.info("Usage summary generated: {} records, total: {}", usageRecords.size(), totalAmount);

        return UsageSummaryResponse.builder()
                .accountId(accountId)
                .periodStart(startDate)
                .periodEnd(endDate)
                .totalUsageRecords((long) usageRecords.size())
                .totalAmount(totalAmount)
                .unbilledRecords(unbilledCount)
                .usageByMetric(usageByMetric)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Aggregates usage for billing
     */
    @Transactional
    public List<UsageRecord> aggregateUsageForBilling(UUID accountId, LocalDateTime start, LocalDateTime end) {
        log.info("Aggregating unbilled usage for account: {}", accountId);

        return usageRecordRepository.findByAccountAndPeriod(accountId, start, end).stream()
                .filter(u -> Boolean.FALSE.equals(u.getBilled()))
                .toList();
    }

    /**
     * Marks usage as billed
     */
    @Transactional
    public void markUsageAsBilled(List<UUID> usageIds, UUID invoiceId) {
        log.info("Marking {} usage records as billed for invoice: {}", usageIds.size(), invoiceId);

        usageRecordRepository.markAsBilled(usageIds, invoiceId, LocalDateTime.now());
    }

    // ==================== Helper Methods ====================

    private BigDecimal lookupUnitPrice(String metricName) {
        // In production, this would query pricing table
        // For now, return default pricing
        return switch (metricName) {
            case "api_calls" -> new BigDecimal("0.001");  // $0.001 per call
            case "storage_gb_hours" -> new BigDecimal("0.023");  // $0.023 per GB-hour
            case "bandwidth_gb" -> new BigDecimal("0.09");  // $0.09 per GB
            case "compute_hours" -> new BigDecimal("0.50");  // $0.50 per hour
            default -> new BigDecimal("0.01");  // Default: $0.01 per unit
        };
    }

    private UsageRecord.MetricCategory determineMetricCategory(String metricName) {
        if (metricName.contains("api") || metricName.contains("request")) {
            return UsageRecord.MetricCategory.API_USAGE;
        } else if (metricName.contains("storage")) {
            return UsageRecord.MetricCategory.STORAGE;
        } else if (metricName.contains("bandwidth")) {
            return UsageRecord.MetricCategory.BANDWIDTH;
        } else if (metricName.contains("compute") || metricName.contains("cpu")) {
            return UsageRecord.MetricCategory.COMPUTE;
        } else if (metricName.contains("transaction")) {
            return UsageRecord.MetricCategory.TRANSACTIONS;
        }
        return UsageRecord.MetricCategory.CUSTOM;
    }

    private void detectUsageAnomaly(UUID accountId, String metricName, BigDecimal quantity) {
        // Get historical average for this metric
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        List<UsageRecord> historicalUsage = usageRecordRepository
                .findByAccountIdAndMetricNameAndUsageTimestampBetween(
                        accountId, metricName, last30Days, LocalDateTime.now());

        if (historicalUsage.isEmpty()) {
            return;  // No historical data
        }

        BigDecimal averageUsage = historicalUsage.stream()
                .map(UsageRecord::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(historicalUsage.size()), RoundingMode.HALF_UP);

        // Check for spike (10x average)
        BigDecimal threshold = averageUsage.multiply(BigDecimal.TEN);
        if (quantity.compareTo(threshold) > 0) {
            log.warn("USAGE ANOMALY DETECTED: Account {} metric {} usage {} exceeds threshold {}",
                    accountId, metricName, quantity, threshold);

            usageAnomalies.increment();

            // Send alert via AlertingService
            alertingService.sendWarningAlert(
                "Usage Anomaly Detected",
                String.format("Account %s metric %s usage %s exceeds threshold %s (10x spike)",
                    accountId, metricName, quantity, threshold),
                "billing-orchestrator-service",
                Map.of(
                    "accountId", accountId,
                    "metricName", metricName,
                    "quantity", quantity,
                    "threshold", threshold,
                    "averageUsage", averageUsage
                )
            );
        }
    }

    private void publishUsageEvent(UsageRecord usageRecord) {
        try {
            String event = String.format(
                    "{\"eventType\":\"USAGE_RECORDED\",\"usageId\":\"%s\",\"accountId\":\"%s\"," +
                    "\"metric\":\"%s\",\"quantity\":\"%s\",\"amount\":\"%s\"}",
                    usageRecord.getId(), usageRecord.getAccountId(), usageRecord.getMetricName(),
                    usageRecord.getQuantity(), usageRecord.getTotalAmount()
            );

            kafkaTemplate.send("billing.usage.recorded", usageRecord.getId().toString(), event);
        } catch (Exception e) {
            log.error("Failed to publish usage event: {}", usageRecord.getId(), e);
        }
    }

    private UsageRecordResponse mapToResponse(UsageRecord usage) {
        return UsageRecordResponse.builder()
                .usageId(usage.getId())
                .accountId(usage.getAccountId())
                .metricName(usage.getMetricName())
                .quantity(usage.getQuantity())
                .unit(usage.getUnit())
                .unitPrice(usage.getUnitPrice())
                .totalAmount(usage.getTotalAmount())
                .currency(usage.getCurrency())
                .timestamp(usage.getUsageTimestamp())
                .status(usage.getStatus().name())
                .billed(usage.getBilled())
                .build();
    }

    // Circuit breaker fallback
    private UsageRecordResponse recordUsageFallback(RecordUsageRequest request, Throwable throwable) {
        log.error("Circuit breaker activated for recordUsage. Error: {}", throwable.getMessage());
        throw new RuntimeException("Usage tracking service temporarily unavailable", throwable);
    }

    // DTO for usage summary
    @lombok.Data
    @lombok.Builder
    public static class UsageMetricSummary {
        private String metricName;
        private BigDecimal totalQuantity;
        private BigDecimal totalAmount;
        private Integer recordCount;
        private String unit;
    }
}
