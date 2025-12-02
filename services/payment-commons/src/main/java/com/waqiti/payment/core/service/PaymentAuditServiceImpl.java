package com.waqiti.payment.core.service;

import com.waqiti.payment.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise Payment Audit Service Implementation
 * 
 * Provides comprehensive audit trail functionality for all payment operations
 * with support for compliance, analytics, and forensic investigation.
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAuditServiceImpl implements PaymentAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, PaymentResult> idempotencyCache = new ConcurrentHashMap<>();
    private final Map<String, AuditRecord> auditRecords = new ConcurrentHashMap<>();
    
    // =====================================================
    // CORE AUDIT OPERATIONS
    // =====================================================
    
    @Override
    @Transactional
    public void auditPayment(PaymentRequest request, PaymentResult result) {
        log.debug("Auditing payment: {} with result: {}", request.getPaymentId(), result.getStatus());
        
        AuditRecord record = AuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .paymentId(request.getPaymentId())
            .userId(request.getUserId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .paymentType(request.getType())
            .providerType(request.getProviderType())
            .status(result.getStatus())
            .timestamp(Instant.now())
            .requestData(serializeRequest(request))
            .responseData(serializeResult(result))
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .build();
        
        saveAuditRecord(record);
        
        // Cache for idempotency
        if (request.getIdempotencyKey() != null) {
            idempotencyCache.put(request.getIdempotencyKey(), result);
        }
    }
    
    @Override
    @Transactional
    public void auditPaymentWithMetadata(PaymentRequest request, PaymentResult result, Map<String, Object> metadata) {
        log.debug("Auditing payment with metadata: {}", request.getPaymentId());
        
        AuditRecord record = AuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .paymentId(request.getPaymentId())
            .userId(request.getUserId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .paymentType(request.getType())
            .providerType(request.getProviderType())
            .status(result.getStatus())
            .timestamp(Instant.now())
            .requestData(serializeRequest(request))
            .responseData(serializeResult(result))
            .metadata(metadata)
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .build();
        
        saveAuditRecord(record);
        
        // Store enhanced audit data
        String sql = """
            INSERT INTO payment_audit_metadata 
            (audit_id, payment_id, metadata_key, metadata_value, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        
        metadata.forEach((key, value) -> {
            jdbcTemplate.update(sql, 
                record.getAuditId(),
                request.getPaymentId(),
                key,
                value != null ? value.toString() : null,
                Timestamp.from(Instant.now())
            );
        });
    }
    
    @Override
    @Transactional
    public void auditRefund(RefundRequest request, PaymentResult result) {
        log.debug("Auditing refund for payment: {}", request.getOriginalPaymentId());
        
        AuditRecord record = AuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .paymentId(request.getOriginalPaymentId())
            .refundId(request.getRefundId())
            .userId(request.getUserId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .paymentType(PaymentType.REFUND)
            .status(result.getStatus())
            .timestamp(Instant.now())
            .requestData(serializeRefundRequest(request))
            .responseData(serializeResult(result))
            .build();
        
        saveAuditRecord(record);
    }
    
    // =====================================================
    // IDEMPOTENCY SUPPORT
    // =====================================================
    
    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        // Check cache first
        if (idempotencyCache.containsKey(idempotencyKey)) {
            return true;
        }
        
        // Check database
        String sql = "SELECT COUNT(*) FROM payment_audit WHERE idempotency_key = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, idempotencyKey);
        return count != null && count > 0;
    }
    
    @Override
    @Cacheable(value = "payment-results", key = "#idempotencyKey")
    public PaymentResult getResultByIdempotencyKey(String idempotencyKey) {
        // Check cache
        PaymentResult cached = idempotencyCache.get(idempotencyKey);
        if (cached != null) {
            return cached;
        }
        
        // Load from database
        String sql = """
            SELECT payment_id, status, amount, currency, transaction_id, 
                   error_message, created_at, response_data
            FROM payment_audit 
            WHERE idempotency_key = ?
            ORDER BY created_at DESC 
            LIMIT 1
            """;
        
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> 
            PaymentResult.builder()
                .paymentId(rs.getString("payment_id"))
                .status(PaymentStatus.valueOf(rs.getString("status")))
                .amount(rs.getBigDecimal("amount"))
                .currency(rs.getString("currency"))
                .transactionId(rs.getString("transaction_id"))
                .errorMessage(rs.getString("error_message"))
                .timestamp(rs.getTimestamp("created_at").toInstant())
                .build(),
            idempotencyKey
        );
    }
    
    @Override
    @CachePut(value = "payment-results", key = "#idempotencyKey")
    public void cacheResult(String idempotencyKey, PaymentResult result) {
        idempotencyCache.put(idempotencyKey, result);
        
        // Also persist to database
        String sql = """
            INSERT INTO payment_idempotency_cache 
            (idempotency_key, payment_id, result_data, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO UPDATE 
            SET result_data = EXCLUDED.result_data,
                updated_at = CURRENT_TIMESTAMP
            """;
        
        jdbcTemplate.update(sql,
            idempotencyKey,
            result.getPaymentId(),
            serializeResult(result),
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now().plusSeconds(86400)) // 24 hour expiry
        );
    }
    
    // =====================================================
    // RETRIEVAL OPERATIONS
    // =====================================================
    
    @Override
    public PaymentResult getPaymentById(String paymentId) {
        String sql = """
            SELECT payment_id, status, amount, currency, transaction_id,
                   error_message, created_at, response_data
            FROM payment_audit
            WHERE payment_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;
        
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                PaymentResult.builder()
                    .paymentId(rs.getString("payment_id"))
                    .status(PaymentStatus.valueOf(rs.getString("status")))
                    .amount(rs.getBigDecimal("amount"))
                    .currency(rs.getString("currency"))
                    .transactionId(rs.getString("transaction_id"))
                    .errorMessage(rs.getString("error_message"))
                    .timestamp(rs.getTimestamp("created_at").toInstant())
                    .build(),
                paymentId
            );
        } catch (Exception e) {
            log.error("Payment not found: {}", paymentId, e);
            return null;
        }
    }
    
    @Override
    public List<PaymentResult> getPaymentHistory(String userId, PaymentHistoryFilter filter) {
        StringBuilder sql = new StringBuilder("""
            SELECT payment_id, status, amount, currency, payment_type,
                   transaction_id, error_message, created_at
            FROM payment_audit
            WHERE user_id = ?
            """);
        
        List<Object> params = new ArrayList<>();
        params.add(userId);
        
        // Apply filters
        if (filter != null) {
            if (filter.getStartDate() != null) {
                sql.append(" AND created_at >= ?");
                params.add(Timestamp.from(filter.getStartDate()));
            }
            
            if (filter.getEndDate() != null) {
                sql.append(" AND created_at <= ?");
                params.add(Timestamp.from(filter.getEndDate()));
            }
            
            if (filter.getPaymentType() != null) {
                sql.append(" AND payment_type = ?");
                params.add(filter.getPaymentType().toString());
            }
            
            if (filter.getStatus() != null) {
                sql.append(" AND status = ?");
                params.add(filter.getStatus().toString());
            }
            
            if (filter.getMinAmount() != null) {
                sql.append(" AND amount >= ?");
                params.add(filter.getMinAmount());
            }
            
            if (filter.getMaxAmount() != null) {
                sql.append(" AND amount <= ?");
                params.add(filter.getMaxAmount());
            }
        }
        
        sql.append(" ORDER BY created_at DESC");
        
        if (filter != null && filter.getLimit() > 0) {
            sql.append(" LIMIT ?");
            params.add(filter.getLimit());
        }
        
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) ->
            PaymentResult.builder()
                .paymentId(rs.getString("payment_id"))
                .status(PaymentStatus.valueOf(rs.getString("status")))
                .amount(rs.getBigDecimal("amount"))
                .currency(rs.getString("currency"))
                .transactionId(rs.getString("transaction_id"))
                .errorMessage(rs.getString("error_message"))
                .timestamp(rs.getTimestamp("created_at").toInstant())
                .build(),
            params.toArray()
        );
    }
    
    // =====================================================
    // ANALYTICS OPERATIONS
    // =====================================================
    
    @Override
    public PaymentAnalytics getPaymentAnalytics(String userId, AnalyticsFilter filter) {
        log.debug("Generating payment analytics for user: {}", userId);
        
        // Get payment data for analysis
        List<PaymentResult> payments = getPaymentHistory(userId, 
            PaymentHistoryFilter.builder()
                .startDate(filter.getStartDate())
                .endDate(filter.getEndDate())
                .build()
        );
        
        // Calculate analytics
        PaymentAnalytics analytics = PaymentAnalytics.builder()
            .userId(userId)
            .period(filter.getPeriod())
            .totalTransactions(payments.size())
            .totalAmount(calculateTotalAmount(payments))
            .averageAmount(calculateAverageAmount(payments))
            .successRate(calculateSuccessRate(payments))
            .failureRate(calculateFailureRate(payments))
            .topPaymentTypes(getTopPaymentTypes(payments))
            .hourlyDistribution(getHourlyDistribution(payments))
            .dailyTrend(getDailyTrend(payments, filter))
            .paymentMethodBreakdown(getPaymentMethodBreakdown(payments))
            .build();
        
        return analytics;
    }
    
    // =====================================================
    // HELPER METHODS
    // =====================================================
    
    private void saveAuditRecord(AuditRecord record) {
        auditRecords.put(record.getAuditId(), record);
        
        String sql = """
            INSERT INTO payment_audit 
            (audit_id, payment_id, user_id, amount, currency, payment_type,
             provider_type, status, request_data, response_data, ip_address,
             user_agent, idempotency_key, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            record.getAuditId(),
            record.getPaymentId(),
            record.getUserId(),
            record.getAmount(),
            record.getCurrency(),
            record.getPaymentType() != null ? record.getPaymentType().toString() : null,
            record.getProviderType() != null ? record.getProviderType().toString() : null,
            record.getStatus() != null ? record.getStatus().toString() : null,
            record.getRequestData(),
            record.getResponseData(),
            record.getIpAddress(),
            record.getUserAgent(),
            record.getIdempotencyKey(),
            Timestamp.from(record.getTimestamp())
        );
    }
    
    private String serializeRequest(PaymentRequest request) {
        // Convert request to JSON string for storage
        Map<String, Object> data = new HashMap<>();
        data.put("paymentId", request.getPaymentId());
        data.put("userId", request.getUserId());
        data.put("amount", request.getAmount());
        data.put("currency", request.getCurrency());
        data.put("type", request.getType());
        data.put("metadata", request.getMetadata());
        return data.toString(); // In production, use proper JSON serialization
    }
    
    private String serializeRefundRequest(RefundRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("refundId", request.getRefundId());
        data.put("originalPaymentId", request.getOriginalPaymentId());
        data.put("amount", request.getAmount());
        data.put("reason", request.getReason());
        return data.toString();
    }
    
    private String serializeResult(PaymentResult result) {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentId", result.getPaymentId());
        data.put("status", result.getStatus());
        data.put("transactionId", result.getTransactionId());
        data.put("errorMessage", result.getErrorMessage());
        return data.toString();
    }
    
    private BigDecimal calculateTotalAmount(List<PaymentResult> payments) {
        return payments.stream()
            .filter(p -> p.getAmount() != null)
            .map(PaymentResult::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateAverageAmount(List<PaymentResult> payments) {
        if (payments.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal total = calculateTotalAmount(payments);
        return total.divide(BigDecimal.valueOf(payments.size()), 2, RoundingMode.HALF_UP);
    }
    
    private double calculateSuccessRate(List<PaymentResult> payments) {
        if (payments.isEmpty()) return 0.0;
        
        long successful = payments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
            .count();
        
        return (double) successful / payments.size() * 100;
    }
    
    private double calculateFailureRate(List<PaymentResult> payments) {
        if (payments.isEmpty()) return 0.0;
        
        long failed = payments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.FAILED || 
                        p.getStatus() == PaymentStatus.ERROR)
            .count();
        
        return (double) failed / payments.size() * 100;
    }
    
    private Map<String, Integer> getTopPaymentTypes(List<PaymentResult> payments) {
        Map<String, Integer> typeCount = new HashMap<>();
        
        String sql = """
            SELECT payment_type, COUNT(*) as count
            FROM payment_audit
            WHERE payment_id IN (%s)
            GROUP BY payment_type
            ORDER BY count DESC
            LIMIT 5
            """;
        
        if (!payments.isEmpty()) {
            String paymentIds = payments.stream()
                .map(PaymentResult::getPaymentId)
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(","));
            
            jdbcTemplate.query(String.format(sql, paymentIds), (rs, rowNum) -> {
                typeCount.put(rs.getString("payment_type"), rs.getInt("count"));
                return null;
            });
        }
        
        return typeCount;
    }
    
    private List<Integer> getHourlyDistribution(List<PaymentResult> payments) {
        List<Integer> hourlyCount = new ArrayList<>(Collections.nCopies(24, 0));
        
        payments.forEach(payment -> {
            if (payment.getTimestamp() != null) {
                int hour = LocalDateTime.ofInstant(payment.getTimestamp(), ZoneId.systemDefault()).getHour();
                hourlyCount.set(hour, hourlyCount.get(hour) + 1);
            }
        });
        
        return hourlyCount;
    }
    
    private List<DailyTrendData> getDailyTrend(List<PaymentResult> payments, AnalyticsFilter filter) {
        List<DailyTrendData> trend = new ArrayList<>();
        
        String sql = """
            SELECT DATE(created_at) as day, COUNT(*) as count, SUM(amount) as total
            FROM payment_audit
            WHERE user_id = ? AND created_at BETWEEN ? AND ?
            GROUP BY DATE(created_at)
            ORDER BY day
            """;
        
        if (filter.getUserId() != null && filter.getStartDate() != null && filter.getEndDate() != null) {
            jdbcTemplate.query(sql, (rs, rowNum) -> {
                trend.add(DailyTrendData.builder()
                    .date(rs.getDate("day").toLocalDate())
                    .transactionCount(rs.getInt("count"))
                    .totalAmount(rs.getBigDecimal("total"))
                    .build());
                return null;
            }, filter.getUserId(), 
               Timestamp.from(filter.getStartDate()),
               Timestamp.from(filter.getEndDate()));
        }
        
        return trend;
    }
    
    private Map<String, Integer> getPaymentMethodBreakdown(List<PaymentResult> payments) {
        Map<String, Integer> breakdown = new HashMap<>();
        
        String sql = """
            SELECT provider_type, COUNT(*) as count
            FROM payment_audit
            WHERE payment_id IN (%s)
            GROUP BY provider_type
            """;
        
        if (!payments.isEmpty()) {
            String paymentIds = payments.stream()
                .map(PaymentResult::getPaymentId)
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(","));
            
            jdbcTemplate.query(String.format(sql, paymentIds), (rs, rowNum) -> {
                breakdown.put(rs.getString("provider_type"), rs.getInt("count"));
                return null;
            });
        }
        
        return breakdown;
    }
    
    // =====================================================
    // INNER CLASSES
    // =====================================================
    
    @lombok.Data
    @lombok.Builder
    private static class AuditRecord {
        private String auditId;
        private String paymentId;
        private String refundId;
        private String userId;
        private BigDecimal amount;
        private String currency;
        private PaymentType paymentType;
        private ProviderType providerType;
        private PaymentStatus status;
        private Instant timestamp;
        private String requestData;
        private String responseData;
        private Map<String, Object> metadata;
        private String ipAddress;
        private String userAgent;
        private String idempotencyKey;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DailyTrendData {
        private java.time.LocalDate date;
        private int transactionCount;
        private BigDecimal totalAmount;
    }
}