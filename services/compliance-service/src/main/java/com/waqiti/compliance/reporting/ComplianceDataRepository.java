package com.waqiti.compliance.reporting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CRITICAL COMPLIANCE: Data repository for regulatory compliance reporting
 * 
 * Handles secure access to compliance data including:
 * - Transaction monitoring data
 * - Customer risk assessments
 * - AML/KYC statistics
 * - Regulatory reporting metrics
 * - Audit trail data
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ComplianceDataRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Get comprehensive KYC statistics for a reporting period
     */
    public KYCStatistics getKYCStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            String sql = """
                SELECT 
                    COUNT(*) as total_customers,
                    COUNT(CASE WHEN kyc_status = 'COMPLETE' THEN 1 END) as complete_kyc,
                    COUNT(CASE WHEN kyc_status = 'INCOMPLETE' THEN 1 END) as incomplete_kyc,
                    COUNT(CASE WHEN kyc_status = 'PENDING' THEN 1 END) as pending_kyc,
                    COUNT(CASE WHEN kyc_status = 'EXPIRED' THEN 1 END) as expired_kyc,
                    COUNT(CASE WHEN risk_level = 'HIGH' THEN 1 END) as high_risk_customers,
                    COUNT(CASE WHEN risk_level = 'MEDIUM' THEN 1 END) as medium_risk_customers,
                    COUNT(CASE WHEN risk_level = 'LOW' THEN 1 END) as low_risk_customers
                FROM users u 
                LEFT JOIN customer_kyc ck ON u.id = ck.user_id
                WHERE u.created_at BETWEEN ? AND ?
                """;

            return jdbcTemplate.queryForObject(sql, new Object[]{startDate, endDate}, 
                (rs, rowNum) -> KYCStatistics.builder()
                    .totalCustomers(rs.getLong("total_customers"))
                    .completeKYCCount(rs.getLong("complete_kyc"))
                    .incompleteKYCCount(rs.getLong("incomplete_kyc"))
                    .pendingKYCCount(rs.getLong("pending_kyc"))
                    .expiredKYCCount(rs.getLong("expired_kyc"))
                    .highRiskCustomerCount(rs.getLong("high_risk_customers"))
                    .mediumRiskCustomerCount(rs.getLong("medium_risk_customers"))
                    .lowRiskCustomerCount(rs.getLong("low_risk_customers"))
                    .build());

        } catch (Exception e) {
            log.error("Error retrieving KYC statistics", e);
            throw new ComplianceDataException("Failed to retrieve KYC statistics", e);
        }
    }

    /**
     * Get comprehensive compliance metrics for monthly reporting
     */
    public ComplianceMetrics getComplianceMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            String sql = """
                SELECT 
                    COALESCE(SUM(t.amount), 0) as total_transaction_volume,
                    COUNT(t.id) as total_transaction_count,
                    COUNT(CASE WHEN t.risk_score > 80 THEN 1 END) as high_risk_transactions,
                    (SELECT COUNT(*) FROM ctr_reports WHERE report_date BETWEEN ? AND ?) as ctr_reports,
                    (SELECT COUNT(*) FROM sar_reports WHERE report_date BETWEEN ? AND ?) as sar_reports,
                    (SELECT COUNT(*) FROM aml_alerts WHERE created_at BETWEEN ? AND ?) as aml_alerts,
                    (SELECT COUNT(*) FROM aml_alerts WHERE created_at BETWEEN ? AND ? AND status = 'RESOLVED') as resolved_alerts,
                    (SELECT COUNT(*) FROM compliance_violations WHERE violation_date BETWEEN ? AND ?) as violations
                FROM transactions t
                WHERE t.created_at BETWEEN ? AND ?
                """;

            return jdbcTemplate.queryForObject(sql, new Object[]{
                startDate, endDate, // CTR reports
                startDate, endDate, // SAR reports
                startDate, endDate, // AML alerts
                startDate, endDate, // Resolved alerts
                startDate, endDate, // Violations
                startDate, endDate  // Transactions
            }, (rs, rowNum) -> ComplianceMetrics.builder()
                .totalTransactionVolume(rs.getBigDecimal("total_transaction_volume"))
                .totalTransactionCount(rs.getLong("total_transaction_count"))
                .highRiskTransactionCount(rs.getLong("high_risk_transactions"))
                .ctrReportsGenerated(rs.getLong("ctr_reports"))
                .sarReportsGenerated(rs.getLong("sar_reports"))
                .amlAlertsGenerated(rs.getLong("aml_alerts"))
                .amlAlertsResolved(rs.getLong("resolved_alerts"))
                .complianceViolations(rs.getLong("violations"))
                .build());

        } catch (Exception e) {
            log.error("Error retrieving compliance metrics", e);
            throw new ComplianceDataException("Failed to retrieve compliance metrics", e);
        }
    }

    /**
     * Get transaction velocity statistics for suspicious activity detection
     */
    public List<TransactionVelocityStats> getTransactionVelocityStats(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            String sql = """
                SELECT 
                    t.user_id,
                    COUNT(*) as transaction_count,
                    SUM(t.amount) as total_amount,
                    AVG(t.amount) as avg_amount,
                    MAX(t.amount) as max_amount,
                    MIN(t.amount) as min_amount,
                    COUNT(DISTINCT DATE(t.created_at)) as active_days,
                    STDDEV(t.amount) as amount_deviation
                FROM transactions t
                WHERE t.created_at BETWEEN ? AND ?
                GROUP BY t.user_id
                HAVING COUNT(*) > 10 OR SUM(t.amount) > 50000
                ORDER BY transaction_count DESC, total_amount DESC
                """;

            return jdbcTemplate.query(sql, new Object[]{startDate, endDate},
                (rs, rowNum) -> TransactionVelocityStats.builder()
                    .userId(rs.getString("user_id"))
                    .transactionCount(rs.getLong("transaction_count"))
                    .totalAmount(rs.getBigDecimal("total_amount"))
                    .averageAmount(rs.getBigDecimal("avg_amount"))
                    .maxAmount(rs.getBigDecimal("max_amount"))
                    .minAmount(rs.getBigDecimal("min_amount"))
                    .activeDays(rs.getInt("active_days"))
                    .amountDeviation(rs.getBigDecimal("amount_deviation"))
                    .build());

        } catch (Exception e) {
            log.error("Error retrieving transaction velocity stats", e);
            throw new ComplianceDataException("Failed to retrieve transaction velocity statistics", e);
        }
    }

    /**
     * Get geographic transaction patterns for AML analysis
     */
    public List<GeographicTransactionPattern> getGeographicPatterns(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            String sql = """
                SELECT 
                    t.originating_country,
                    t.destination_country,
                    COUNT(*) as transaction_count,
                    SUM(t.amount) as total_amount,
                    AVG(t.amount) as avg_amount,
                    COUNT(DISTINCT t.user_id) as unique_customers
                FROM transactions t
                WHERE t.created_at BETWEEN ? AND ?
                  AND (t.originating_country != t.destination_country OR t.amount > 10000)
                GROUP BY t.originating_country, t.destination_country
                ORDER BY transaction_count DESC
                """;

            return jdbcTemplate.query(sql, new Object[]{startDate, endDate},
                (rs, rowNum) -> GeographicTransactionPattern.builder()
                    .originatingCountry(rs.getString("originating_country"))
                    .destinationCountry(rs.getString("destination_country"))
                    .transactionCount(rs.getLong("transaction_count"))
                    .totalAmount(rs.getBigDecimal("total_amount"))
                    .averageAmount(rs.getBigDecimal("avg_amount"))
                    .uniqueCustomers(rs.getLong("unique_customers"))
                    .build());

        } catch (Exception e) {
            log.error("Error retrieving geographic transaction patterns", e);
            throw new ComplianceDataException("Failed to retrieve geographic patterns", e);
        }
    }

    /**
     * Get customer risk distribution for compliance reporting
     */
    public Map<String, Long> getCustomerRiskDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            String sql = """
                SELECT 
                    cr.risk_level,
                    COUNT(*) as customer_count
                FROM customer_risk cr
                JOIN users u ON cr.user_id = u.id
                WHERE u.created_at BETWEEN ? AND ?
                GROUP BY cr.risk_level
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, startDate, endDate);
            return results.stream()
                .collect(java.util.stream.Collectors.toMap(
                    row -> (String) row.get("risk_level"),
                    row -> (Long) row.get("customer_count")
                ));

        } catch (Exception e) {
            log.error("Error retrieving customer risk distribution", e);
            throw new ComplianceDataException("Failed to retrieve risk distribution", e);
        }
    }

    /**
     * Get compliance training completion rates
     */
    public double getComplianceTrainingCompletionRate(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            String sql = """
                SELECT 
                    COUNT(*) as total_employees,
                    COUNT(CASE WHEN ct.completion_date IS NOT NULL 
                              AND ct.completion_date BETWEEN ? AND ? THEN 1 END) as completed_training
                FROM employees e
                LEFT JOIN compliance_training ct ON e.id = ct.employee_id
                WHERE e.active = true
                """;

            return jdbcTemplate.queryForObject(sql, new Object[]{startDate, endDate}, 
                (rs, rowNum) -> {
                    long total = rs.getLong("total_employees");
                    long completed = rs.getLong("completed_training");
                    return total > 0 ? (double) completed / total * 100.0 : 0.0;
                });

        } catch (Exception e) {
            log.error("Error retrieving training completion rate", e);
            return 0.0;
        }
    }

    /**
     * Save compliance report entity
     */
    public void saveReport(ComplianceReportEntity report) {
        try {
            String sql = """
                INSERT INTO compliance_reports 
                (report_id, report_type, encrypted_data, generated_at, status, retention_date)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

            int result = jdbcTemplate.update(sql,
                report.getReportId(),
                report.getReportType(),
                report.getEncryptedData(),
                report.getGeneratedAt(),
                report.getStatus().name(),
                report.getRetentionDate());

            if (result != 1) {
                throw new ComplianceDataException("Failed to save compliance report");
            }

            log.info("Saved compliance report: {} - {}", report.getReportType(), report.getReportId());

        } catch (Exception e) {
            log.error("Error saving compliance report", e);
            throw new ComplianceDataException("Failed to save compliance report", e);
        }
    }

    /**
     * Get suspicious transaction patterns for SAR reporting
     */
    public List<SuspiciousTransactionPattern> getSuspiciousTransactionPatterns(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            String sql = """
                SELECT 
                    t.user_id,
                    t.id as transaction_id,
                    t.amount,
                    t.transaction_type,
                    t.created_at,
                    t.risk_score,
                    t.ml_flags,
                    u.risk_level as customer_risk_level,
                    COUNT(*) OVER (PARTITION BY t.user_id) as user_transaction_count,
                    SUM(t.amount) OVER (PARTITION BY t.user_id) as user_total_amount,
                    CASE 
                        WHEN t.amount > 9000 AND t.transaction_type = 'CASH_DEPOSIT' THEN 'STRUCTURING_SUSPECTED'
                        WHEN t.risk_score > 85 THEN 'HIGH_RISK_ML_DETECTION'
                        WHEN COUNT(*) OVER (PARTITION BY t.user_id, DATE(t.created_at)) > 5 THEN 'HIGH_VELOCITY'
                        WHEN t.amount > 50000 THEN 'LARGE_TRANSACTION'
                        ELSE 'OTHER'
                    END as suspicion_type
                FROM transactions t
                JOIN users u ON t.user_id = u.id
                WHERE t.created_at BETWEEN ? AND ?
                  AND (t.risk_score > 75 OR t.amount > 5000 OR t.ml_flags IS NOT NULL)
                ORDER BY t.risk_score DESC, t.amount DESC
                """;

            return jdbcTemplate.query(sql, new Object[]{startDate, endDate},
                (rs, rowNum) -> SuspiciousTransactionPattern.builder()
                    .userId(rs.getString("user_id"))
                    .transactionId(rs.getString("transaction_id"))
                    .amount(rs.getBigDecimal("amount"))
                    .transactionType(rs.getString("transaction_type"))
                    .transactionDate(rs.getTimestamp("created_at").toLocalDateTime())
                    .riskScore(rs.getDouble("risk_score"))
                    .mlFlags(rs.getString("ml_flags"))
                    .customerRiskLevel(rs.getString("customer_risk_level"))
                    .userTransactionCount(rs.getLong("user_transaction_count"))
                    .userTotalAmount(rs.getBigDecimal("user_total_amount"))
                    .suspicionType(rs.getString("suspicion_type"))
                    .build());

        } catch (Exception e) {
            log.error("Error retrieving suspicious transaction patterns", e);
            throw new ComplianceDataException("Failed to retrieve suspicious patterns", e);
        }
    }

    // Data classes for compliance metrics

    @lombok.Data
    @lombok.Builder
    public static class KYCStatistics {
        private long totalCustomers;
        private long completeKYCCount;
        private long incompleteKYCCount;
        private long pendingKYCCount;
        private long expiredKYCCount;
        private long highRiskCustomerCount;
        private long mediumRiskCustomerCount;
        private long lowRiskCustomerCount;
    }

    @lombok.Data
    @lombok.Builder
    public static class ComplianceMetrics {
        private BigDecimal totalTransactionVolume;
        private long totalTransactionCount;
        private long highRiskTransactionCount;
        private long ctrReportsGenerated;
        private long sarReportsGenerated;
        private long amlAlertsGenerated;
        private long amlAlertsResolved;
        private double kycComplianceRate;
        private Map<String, Long> customerRiskDistribution;
        private double trainingCompletionRate;
        private long regulatoryExaminations;
        private long complianceViolations;
        private long correctiveActions;
    }

    @lombok.Data
    @lombok.Builder
    public static class TransactionVelocityStats {
        private String userId;
        private long transactionCount;
        private BigDecimal totalAmount;
        private BigDecimal averageAmount;
        private BigDecimal maxAmount;
        private BigDecimal minAmount;
        private int activeDays;
        private BigDecimal amountDeviation;
    }

    @lombok.Data
    @lombok.Builder
    public static class GeographicTransactionPattern {
        private String originatingCountry;
        private String destinationCountry;
        private long transactionCount;
        private BigDecimal totalAmount;
        private BigDecimal averageAmount;
        private long uniqueCustomers;
    }

    @lombok.Data
    @lombok.Builder
    public static class SuspiciousTransactionPattern {
        private String userId;
        private String transactionId;
        private BigDecimal amount;
        private String transactionType;
        private LocalDateTime transactionDate;
        private double riskScore;
        private String mlFlags;
        private String customerRiskLevel;
        private long userTransactionCount;
        private BigDecimal userTotalAmount;
        private String suspicionType;
    }

    @lombok.Data
    @lombok.Builder
    public static class ComplianceReportEntity {
        private String reportId;
        private String reportType;
        private String encryptedData;
        private LocalDateTime generatedAt;
        private ReportStatus status;
        private LocalDateTime retentionDate;
    }

    public static class ComplianceDataException extends RuntimeException {
        public ComplianceDataException(String message) {
            super(message);
        }
        
        public ComplianceDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}