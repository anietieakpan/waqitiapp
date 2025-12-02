package com.waqiti.security.sox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository for Segregation of Duties data access
 *
 * DATABASE SCHEMA:
 * - user_roles: User role assignments
 * - transaction_actions: Actions performed on transactions
 * - maker_checker_records: Maker-checker audit trail
 * - sod_violations: Historical SoD violations
 *
 * @author Waqiti Compliance Team
 * @version 3.0.0
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class SegregationOfDutiesRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Get all active roles for a user
     */
    public Set<String> getUserRoles(UUID userId) {
        String sql = "SELECT r.name FROM roles r " +
                    "INNER JOIN user_roles ur ON r.id = ur.role_id " +
                    "WHERE ur.user_id = ?::uuid AND ur.status = 'ACTIVE'";

        List<String> roles = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> rs.getString("name"),
            userId.toString()
        );

        return new HashSet<>(roles);
    }

    /**
     * Get all actions performed on a transaction
     */
    public List<SegregationOfDutiesValidator.TransactionAction> getTransactionActions(UUID transactionId) {
        String sql = "SELECT user_id, action, performed_at " +
                    "FROM transaction_actions " +
                    "WHERE transaction_id = ?::uuid " +
                    "ORDER BY performed_at ASC";

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new SegregationOfDutiesValidator.TransactionAction(
                UUID.fromString(rs.getString("user_id")),
                rs.getString("action"),
                rs.getTimestamp("performed_at").toLocalDateTime()
            ),
            transactionId.toString()
        );
    }

    /**
     * Record a transaction action for SoD audit trail
     */
    public void recordTransactionAction(UUID transactionId, UUID userId, String action) {
        String sql = "INSERT INTO transaction_actions " +
                    "(transaction_id, user_id, action, performed_at) " +
                    "VALUES (?::uuid, ?::uuid, ?, ?)";

        jdbcTemplate.update(
            sql,
            transactionId.toString(),
            userId.toString(),
            action,
            LocalDateTime.now()
        );

        log.debug("Recorded transaction action: Transaction={}, User={}, Action={}",
            transactionId, userId, action);
    }

    /**
     * Record maker-checker pair for audit trail
     */
    public void recordMakerChecker(UUID transactionId, UUID makerId, UUID checkerId) {
        String sql = "INSERT INTO maker_checker_records " +
                    "(transaction_id, maker_id, checker_id, recorded_at) " +
                    "VALUES (?::uuid, ?::uuid, ?::uuid, ?)";

        jdbcTemplate.update(
            sql,
            transactionId.toString(),
            makerId.toString(),
            checkerId.toString(),
            LocalDateTime.now()
        );

        log.debug("Recorded maker-checker: Transaction={}, Maker={}, Checker={}",
            transactionId, makerId, checkerId);
    }

    /**
     * Record a SoD violation for compliance reporting
     */
    public void recordSoDViolation(UUID userId, String violationType, String action1,
                                   String action2, String description) {
        String sql = "INSERT INTO sod_violations " +
                    "(user_id, violation_type, action_1, action_2, description, detected_at) " +
                    "VALUES (?::uuid, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(
            sql,
            userId != null ? userId.toString() : null,
            violationType,
            action1,
            action2,
            description,
            LocalDateTime.now()
        );

        log.warn("Recorded SoD violation: User={}, Type={}, Description={}",
            userId, violationType, description);
    }

    /**
     * Get SoD violations for a user within a time period
     */
    public List<SoDViolationRecord> getUserViolations(UUID userId, LocalDateTime startDate,
                                                       LocalDateTime endDate) {
        String sql = "SELECT id, user_id, violation_type, action_1, action_2, description, " +
                    "detected_at, resolved, resolved_at, resolved_by " +
                    "FROM sod_violations " +
                    "WHERE user_id = ?::uuid AND detected_at BETWEEN ? AND ? " +
                    "ORDER BY detected_at DESC";

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> SoDViolationRecord.builder()
                .id(UUID.fromString(rs.getString("id")))
                .userId(UUID.fromString(rs.getString("user_id")))
                .violationType(rs.getString("violation_type"))
                .action1(rs.getString("action_1"))
                .action2(rs.getString("action_2"))
                .description(rs.getString("description"))
                .detectedAt(rs.getTimestamp("detected_at").toLocalDateTime())
                .resolved(rs.getBoolean("resolved"))
                .resolvedAt(rs.getTimestamp("resolved_at") != null ?
                    rs.getTimestamp("resolved_at").toLocalDateTime() : null)
                .resolvedBy(rs.getString("resolved_by") != null ?
                    UUID.fromString(rs.getString("resolved_by")) : null)
                .build(),
            userId.toString(),
            startDate,
            endDate
        );
    }

    /**
     * Get all unresolved SoD violations
     */
    public List<SoDViolationRecord> getUnresolvedViolations() {
        String sql = "SELECT id, user_id, violation_type, action_1, action_2, description, " +
                    "detected_at, resolved, resolved_at, resolved_by " +
                    "FROM sod_violations " +
                    "WHERE resolved = false " +
                    "ORDER BY detected_at DESC";

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> SoDViolationRecord.builder()
                .id(UUID.fromString(rs.getString("id")))
                .userId(UUID.fromString(rs.getString("user_id")))
                .violationType(rs.getString("violation_type"))
                .action1(rs.getString("action_1"))
                .action2(rs.getString("action_2"))
                .description(rs.getString("description"))
                .detectedAt(rs.getTimestamp("detected_at").toLocalDateTime())
                .resolved(false)
                .build()
        );
    }

    /**
     * Mark a SoD violation as resolved
     */
    public void resolveViolation(UUID violationId, UUID resolvedBy, String resolution) {
        String sql = "UPDATE sod_violations " +
                    "SET resolved = true, resolved_at = ?, resolved_by = ?::uuid, " +
                    "resolution = ? " +
                    "WHERE id = ?::uuid";

        jdbcTemplate.update(
            sql,
            LocalDateTime.now(),
            resolvedBy.toString(),
            resolution,
            violationId.toString()
        );

        log.info("Resolved SoD violation: ViolationId={}, ResolvedBy={}", violationId, resolvedBy);
    }

    /**
     * Check if user has performed specific action on transaction
     */
    public boolean hasUserPerformedAction(UUID userId, UUID transactionId, String action) {
        String sql = "SELECT COUNT(*) FROM transaction_actions " +
                    "WHERE transaction_id = ?::uuid AND user_id = ?::uuid AND action = ?";

        Integer count = jdbcTemplate.queryForObject(
            sql,
            Integer.class,
            transactionId.toString(),
            userId.toString(),
            action
        );

        return count != null && count > 0;
    }

    /**
     * Get transaction statistics for SoD monitoring
     */
    public SoDStatistics getStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        // Total violations
        String violationsSql = "SELECT COUNT(*) FROM sod_violations " +
                              "WHERE detected_at BETWEEN ? AND ?";
        Integer totalViolations = jdbcTemplate.queryForObject(
            violationsSql, Integer.class, startDate, endDate
        );

        // Violations by type
        String byTypeSql = "SELECT violation_type, COUNT(*) as count " +
                          "FROM sod_violations " +
                          "WHERE detected_at BETWEEN ? AND ? " +
                          "GROUP BY violation_type";
        Map<String, Long> violationsByType = jdbcTemplate.query(
            byTypeSql,
            rs -> {
                Map<String, Long> map = new HashMap<>();
                while (rs.next()) {
                    map.put(rs.getString("violation_type"), rs.getLong("count"));
                }
                return map;
            },
            startDate,
            endDate
        );

        // Unresolved violations
        String unresolvedSql = "SELECT COUNT(*) FROM sod_violations " +
                              "WHERE resolved = false";
        Integer unresolvedViolations = jdbcTemplate.queryForObject(unresolvedSql, Integer.class);

        return SoDStatistics.builder()
            .totalViolations(totalViolations != null ? totalViolations : 0)
            .violationsByType(violationsByType)
            .unresolvedViolations(unresolvedViolations != null ? unresolvedViolations : 0)
            .periodStart(startDate)
            .periodEnd(endDate)
            .build();
    }

    /**
     * SoD Violation Record
     */
    @lombok.Data
    @lombok.Builder
    public static class SoDViolationRecord {
        private UUID id;
        private UUID userId;
        private String violationType;
        private String action1;
        private String action2;
        private String description;
        private LocalDateTime detectedAt;
        private boolean resolved;
        private LocalDateTime resolvedAt;
        private UUID resolvedBy;
        private String resolution;
    }

    /**
     * SoD Statistics
     */
    @lombok.Data
    @lombok.Builder
    public static class SoDStatistics {
        private int totalViolations;
        private Map<String, Long> violationsByType;
        private int unresolvedViolations;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
    }
}
