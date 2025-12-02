package com.waqiti.currency.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Conversion Audit Repository
 *
 * Stores audit trail for currency conversions for compliance
 * In production, this would persist to a database
 */
@Slf4j
@Repository
public class ConversionAuditRepository {

    // In-memory storage (in production, use JPA/database)
    private final Map<String, List<AuditEntry>> auditStore = new ConcurrentHashMap<>();

    /**
     * Save audit entry
     */
    public void save(String conversionId, String action, String details, String correlationId) {
        log.debug("Saving audit entry: conversionId={} action={} correlationId={}",
                conversionId, action, correlationId);

        AuditEntry entry = new AuditEntry(
                conversionId,
                action,
                details,
                Instant.now(),
                correlationId
        );

        auditStore.computeIfAbsent(conversionId, k -> new ArrayList<>()).add(entry);

        log.debug("Audit entry saved: conversionId={} action={} entries={}",
                conversionId, action, auditStore.get(conversionId).size());
    }

    /**
     * Find audit entries by conversion ID
     */
    public List<AuditEntry> findByConversionId(String conversionId, String correlationId) {
        log.debug("Finding audit entries: conversionId={} correlationId={}",
                conversionId, correlationId);

        List<AuditEntry> entries = auditStore.getOrDefault(conversionId, new ArrayList<>());

        log.debug("Found {} audit entries for conversionId={} correlationId={}",
                entries.size(), conversionId, correlationId);

        return new ArrayList<>(entries);
    }

    /**
     * Find audit entries by action
     */
    public List<AuditEntry> findByAction(String action, String correlationId) {
        log.debug("Finding audit entries by action: action={} correlationId={}",
                action, correlationId);

        List<AuditEntry> entries = auditStore.values().stream()
                .flatMap(List::stream)
                .filter(entry -> action.equals(entry.action()))
                .collect(Collectors.toList());

        log.debug("Found {} audit entries for action {}: correlationId={}",
                entries.size(), action, correlationId);

        return entries;
    }

    /**
     * Find audit entries within date range
     */
    public List<AuditEntry> findByDateRange(Instant startDate, Instant endDate,
                                           String correlationId) {
        log.debug("Finding audit entries by date range: start={} end={} correlationId={}",
                startDate, endDate, correlationId);

        List<AuditEntry> entries = auditStore.values().stream()
                .flatMap(List::stream)
                .filter(entry -> !entry.timestamp().isBefore(startDate) &&
                               !entry.timestamp().isAfter(endDate))
                .collect(Collectors.toList());

        log.debug("Found {} audit entries in date range: correlationId={}",
                entries.size(), correlationId);

        return entries;
    }

    /**
     * Find audit entries by correlation ID
     */
    public List<AuditEntry> findByCorrelationId(String correlationId) {
        log.debug("Finding audit entries by correlationId: {}", correlationId);

        List<AuditEntry> entries = auditStore.values().stream()
                .flatMap(List::stream)
                .filter(entry -> correlationId.equals(entry.correlationId()))
                .collect(Collectors.toList());

        log.debug("Found {} audit entries for correlationId={}",
                entries.size(), correlationId);

        return entries;
    }

    /**
     * Get all audit entries
     */
    public List<AuditEntry> findAll(String correlationId) {
        log.debug("Finding all audit entries: correlationId={}", correlationId);

        List<AuditEntry> allEntries = auditStore.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        log.debug("Found {} total audit entries: correlationId={}",
                allEntries.size(), correlationId);

        return allEntries;
    }

    /**
     * Count entries by conversion ID
     */
    public long countByConversionId(String conversionId) {
        return auditStore.getOrDefault(conversionId, new ArrayList<>()).size();
    }

    /**
     * Count entries by action
     */
    public long countByAction(String action) {
        return auditStore.values().stream()
                .flatMap(List::stream)
                .filter(entry -> action.equals(entry.action()))
                .count();
    }

    /**
     * Delete audit entries for conversion
     */
    public void deleteByConversionId(String conversionId, String correlationId) {
        log.info("Deleting audit entries: conversionId={} correlationId={}",
                conversionId, correlationId);

        List<AuditEntry> removed = auditStore.remove(conversionId);

        if (removed != null) {
            log.info("Deleted {} audit entries for conversionId={} correlationId={}",
                    removed.size(), conversionId, correlationId);
        } else {
            log.warn("No audit entries found to delete: conversionId={} correlationId={}",
                    conversionId, correlationId);
        }
    }

    /**
     * Get audit statistics
     */
    public AuditStats getStats() {
        long totalEntries = auditStore.values().stream()
                .mapToLong(List::size)
                .sum();

        long totalConversions = auditStore.size();

        Map<String, Long> actionCounts = auditStore.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                    AuditEntry::action,
                    Collectors.counting()
                ));

        return new AuditStats(
                totalEntries,
                totalConversions,
                actionCounts
        );
    }

    /**
     * Audit entry record
     */
    public record AuditEntry(
            String conversionId,
            String action,
            String details,
            Instant timestamp,
            String correlationId
    ) {}

    /**
     * Audit statistics record
     */
    public record AuditStats(
            long totalEntries,
            long totalConversions,
            Map<String, Long> actionCounts
    ) {}
}
