package com.waqiti.dispute.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for dispute analysis, pattern detection, and analytics
 *
 * Provides comprehensive dispute analysis including:
 * - Resolution analytics and reporting
 * - Pattern detection for fraud prevention
 * - Customer dispute history tracking
 * - Merchant performance metrics
 *
 * @author Waqiti Dispute Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisputeAnalysisService {

    /**
     * Update resolution analytics after dispute resolution
     *
     * @param disputeId Dispute identifier
     * @param customerId Customer identifier
     * @param resolutionType Type of resolution (AI, manual, etc.)
     * @param resolutionDecision Final decision
     * @param disputeAmount Amount in dispute
     * @param disputeCategory Category of dispute
     * @param aiConfidenceScore AI confidence score if applicable
     * @param riskScore Risk assessment score
     */
    @Transactional
    public void updateResolutionAnalytics(UUID disputeId, UUID customerId,
            String resolutionType, String resolutionDecision,
            BigDecimal disputeAmount, String disputeCategory,
            String aiConfidenceScore, String riskScore) {

        log.info("Updating dispute analytics: disputeId={}, decision={}, amount={}",
                disputeId, resolutionDecision, disputeAmount);

        try {
            // Track resolution patterns
            trackResolutionPattern(resolutionType, resolutionDecision, disputeCategory);

            // Update customer dispute history
            updateCustomerDisputeHistory(customerId, resolutionDecision, disputeAmount);

            // Calculate dispute rates
            calculateDisputeRates(customerId, disputeCategory);

            // Update fraud indicators
            updateFraudIndicators(customerId, riskScore, resolutionDecision);

            // Track AI performance if applicable
            if ("AUTOMATED_AI".equals(resolutionType)) {
                trackAIPerformance(aiConfidenceScore, resolutionDecision);
            }

            log.debug("Analytics updated successfully for dispute: {}", disputeId);

        } catch (Exception e) {
            log.error("Failed to update dispute analytics for dispute: {}", disputeId, e);
            // Don't throw - analytics failures should not block processing
        }
    }

    /**
     * Track resolution patterns for analysis
     */
    private void trackResolutionPattern(String resolutionType, String resolutionDecision, String category) {
        log.debug("Tracking resolution pattern: type={}, decision={}, category={}",
                resolutionType, resolutionDecision, category);

        // FIXED: Store in analytics database via JDBC
        try {
            String sql = """
                INSERT INTO dispute_analytics (
                    resolution_type, resolution_decision, category,
                    recorded_at, event_type
                ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'RESOLUTION_PATTERN')
                """;

            jdbcTemplate.update(sql, resolutionType, resolutionDecision, category);
            log.debug("Resolution pattern tracked successfully");

        } catch (Exception e) {
            log.error("Failed to track resolution pattern", e);
        }
    }

    /**
     * Update customer's dispute history
     */
    private void updateCustomerDisputeHistory(UUID customerId, String resolutionDecision, BigDecimal amount) {
        log.debug("Updating customer dispute history: customerId={}, decision={}, amount={}",
                customerId, resolutionDecision, amount);

        // FIXED: Update customer profile with dispute history
        try {
            String sql = """
                INSERT INTO customer_dispute_history (
                    customer_id, total_disputes, total_won, total_lost,
                    total_amount_disputed, last_dispute_at, updated_at
                ) VALUES (?, 1, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (customer_id) DO UPDATE SET
                    total_disputes = customer_dispute_history.total_disputes + 1,
                    total_won = customer_dispute_history.total_won + ?,
                    total_lost = customer_dispute_history.total_lost + ?,
                    total_amount_disputed = customer_dispute_history.total_amount_disputed + EXCLUDED.total_amount_disputed,
                    last_dispute_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """;

            int won = "RESOLVED_IN_FAVOR_OF_CUSTOMER".equals(resolutionDecision) ? 1 : 0;
            int lost = "RESOLVED_IN_FAVOR_OF_MERCHANT".equals(resolutionDecision) ? 1 : 0;

            jdbcTemplate.update(sql, customerId, won, lost, amount, won, lost);
            log.debug("Customer dispute history updated successfully");

        } catch (Exception e) {
            log.error("Failed to update customer dispute history", e);
        }
    }

    /**
     * Calculate and update dispute rates
     */
    private void calculateDisputeRates(UUID customerId, String category) {
        log.debug("Calculating dispute rates for customer: {}, category: {}", customerId, category);

        // FIXED: Calculate dispute metrics
        try {
            // Get customer's transaction count (last 90 days)
            String txCountSql = """
                SELECT COUNT(*) FROM transactions_partitioned
                WHERE (source_wallet_id IN (SELECT wallet_id FROM wallets WHERE user_id = ?)
                   OR target_wallet_id IN (SELECT wallet_id FROM wallets WHERE user_id = ?))
                AND transaction_date >= CURRENT_DATE - INTERVAL '90 days'
                """;
            Integer txCount = jdbcTemplate.queryForObject(txCountSql, Integer.class, customerId, customerId);

            // Get dispute stats
            String disputeStatsSql = """
                SELECT
                    COUNT(*) as total_disputes,
                    SUM(CASE WHEN resolution_decision = 'RESOLVED_IN_FAVOR_OF_CUSTOMER' THEN 1 ELSE 0 END) as won,
                    SUM(CASE WHEN category = ? THEN 1 ELSE 0 END) as category_disputes
                FROM disputes
                WHERE customer_id = ?
                AND created_at >= CURRENT_DATE - INTERVAL '90 days'
                """;

            Map<String, Object> stats = jdbcTemplate.queryForMap(disputeStatsSql, category, customerId);

            int totalDisputes = ((Number) stats.get("total_disputes")).intValue();
            int won = ((Number) stats.get("won")).intValue();
            int categoryDisputes = ((Number) stats.get("category_disputes")).intValue();

            double disputeRate = txCount > 0 ? (totalDisputes * 100.0 / txCount) : 0;
            double winRate = totalDisputes > 0 ? (won * 100.0 / totalDisputes) : 0;
            double categoryRate = totalDisputes > 0 ? (categoryDisputes * 100.0 / totalDisputes) : 0;

            log.info("Dispute rates calculated - disputeRate: {}%, winRate: {}%, categoryRate: {}%",
                    String.format("%.2f", disputeRate),
                    String.format("%.2f", winRate),
                    String.format("%.2f", categoryRate));

            // Store calculated rates
            String updateSql = """
                INSERT INTO customer_dispute_metrics (
                    customer_id, dispute_rate, win_rate, category_rate,
                    category, calculated_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

            jdbcTemplate.update(updateSql, customerId, disputeRate, winRate, categoryRate, category);

        } catch (Exception e) {
            log.error("Failed to calculate dispute rates", e);
        }
    }

    /**
     * Update fraud indicators based on dispute patterns
     */
    private void updateFraudIndicators(UUID customerId, String riskScore, String resolutionDecision) {
        log.debug("Updating fraud indicators: customerId={}, riskScore={}, decision={}",
                customerId, riskScore, resolutionDecision);

        // FIXED: Update fraud scoring system
        try {
            // Calculate fraud score adjustment based on dispute outcome
            int scoreAdjustment = 0;
            if ("RESOLVED_IN_FAVOR_OF_MERCHANT".equals(resolutionDecision)) {
                scoreAdjustment = 5; // Lost dispute = higher fraud risk
            } else if ("RESOLVED_IN_FAVOR_OF_CUSTOMER".equals(resolutionDecision)) {
                scoreAdjustment = -2; // Won dispute = slight risk reduction
            }

            // Update fraud score
            String sql = """
                UPDATE user_fraud_scores
                SET fraud_score = LEAST(100, GREATEST(0, fraud_score + ?)),
                    last_dispute_adjustment = ?,
                    last_updated = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;

            int rowsUpdated = jdbcTemplate.update(sql, scoreAdjustment, scoreAdjustment, customerId);

            // Insert if doesn't exist
            if (rowsUpdated == 0) {
                String insertSql = """
                    INSERT INTO user_fraud_scores (
                        user_id, fraud_score, last_dispute_adjustment, last_updated
                    ) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    """;
                jdbcTemplate.update(insertSql, customerId, Math.max(0, 50 + scoreAdjustment), scoreAdjustment);
            }

            log.debug("Fraud indicators updated: adjustment={}", scoreAdjustment);

        } catch (Exception e) {
            log.error("Failed to update fraud indicators", e);
        }
    }

    /**
     * Track AI performance metrics
     */
    private void trackAIPerformance(String aiConfidenceScore, String resolutionDecision) {
        log.debug("Tracking AI performance: confidence={}, decision={}", aiConfidenceScore, resolutionDecision);

        // FIXED: Track AI accuracy and confidence correlation
        try {
            double confidence = aiConfidenceScore != null ? Double.parseDouble(aiConfidenceScore) : 0.0;

            String sql = """
                INSERT INTO ai_performance_metrics (
                    ai_confidence, resolution_decision, prediction_correct,
                    recorded_at
                ) VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """;

            // Assume AI predicted customer favor if confidence > 0.5
            boolean aiPredictedCustomer = confidence > 0.5;
            boolean actuallyCustomer = "RESOLVED_IN_FAVOR_OF_CUSTOMER".equals(resolutionDecision);
            boolean predictionCorrect = aiPredictedCustomer == actuallyCustomer;

            jdbcTemplate.update(sql, confidence, resolutionDecision, predictionCorrect);

            log.debug("AI performance tracked: confidence={}, correct={}", confidence, predictionCorrect);

        } catch (Exception e) {
            log.error("Failed to track AI performance", e);
        }
    }

    /**
     * Analyze dispute patterns for a customer
     *
     * @param customerId Customer identifier
     * @return Analysis results
     */
    @Transactional(readOnly = true)
    public String analyzeCustomerDisputePatterns(UUID customerId) {
        log.info("Analyzing dispute patterns for customer: {}", customerId);

        // FIXED: Comprehensive pattern analysis implementation
        try {
            StringBuilder analysis = new StringBuilder();

            // Temporal patterns
            String temporalSql = """
                SELECT
                    EXTRACT(HOUR FROM created_at) as hour_of_day,
                    EXTRACT(DOW FROM created_at) as day_of_week,
                    COUNT(*) as dispute_count
                FROM disputes
                WHERE customer_id = ?
                AND created_at >= CURRENT_DATE - INTERVAL '90 days'
                GROUP BY hour_of_day, day_of_week
                ORDER BY dispute_count DESC
                LIMIT 5
                """;

            List<Map<String, Object>> temporalPatterns = jdbcTemplate.queryForList(temporalSql, customerId);
            analysis.append("TEMPORAL_PATTERNS: ");
            temporalPatterns.forEach(row -> analysis.append(String.format("Hour=%s,DOW=%s,Count=%s; ",
                    row.get("hour_of_day"), row.get("day_of_week"), row.get("dispute_count"))));

            // Amount patterns
            String amountSql = """
                SELECT
                    ROUND(amount / 100) * 100 as amount_bucket,
                    COUNT(*) as count
                FROM disputes
                WHERE customer_id = ?
                GROUP BY amount_bucket
                ORDER BY count DESC
                LIMIT 3
                """;

            List<Map<String, Object>> amountPatterns = jdbcTemplate.queryForList(amountSql, customerId);
            analysis.append("AMOUNT_PATTERNS: ");
            amountPatterns.forEach(row -> analysis.append(String.format("$%s,Count=%s; ",
                    row.get("amount_bucket"), row.get("count"))));

            // Merchant patterns
            String merchantSql = """
                SELECT merchant_name, COUNT(*) as count
                FROM disputes
                WHERE customer_id = ?
                GROUP BY merchant_name
                ORDER BY count DESC
                LIMIT 5
                """;

            List<Map<String, Object>> merchantPatterns = jdbcTemplate.queryForList(merchantSql, customerId);
            analysis.append("MERCHANT_PATTERNS: ");
            merchantPatterns.forEach(row -> analysis.append(String.format("%s=%s; ",
                    row.get("merchant_name"), row.get("count"))));

            // Category and success rate
            String categorySql = """
                SELECT
                    category,
                    COUNT(*) as total,
                    SUM(CASE WHEN resolution_decision = 'RESOLVED_IN_FAVOR_OF_CUSTOMER' THEN 1 ELSE 0 END) as won
                FROM disputes
                WHERE customer_id = ?
                GROUP BY category
                """;

            List<Map<String, Object>> categoryPatterns = jdbcTemplate.queryForList(categorySql, customerId);
            analysis.append("CATEGORY_PATTERNS: ");
            categoryPatterns.forEach(row -> {
                int total = ((Number) row.get("total")).intValue();
                int won = ((Number) row.get("won")).intValue();
                double winRate = total > 0 ? (won * 100.0 / total) : 0;
                analysis.append(String.format("%s=%.0f%%,Total=%s; ",
                        row.get("category"), winRate, total));
            });

            log.info("Pattern analysis complete for customer: {}", customerId);
            return analysis.toString();

        } catch (Exception e) {
            log.error("Failed to analyze customer dispute patterns", e);
            return "ANALYSIS_FAILED: " + e.getMessage();
        }
    }

    /**
     * Calculate risk score for a new dispute
     *
     * @param customerId Customer identifier
     * @param disputeAmount Amount
     * @param category Category
     * @return Risk score (0-100)
     */
    @Transactional(readOnly = true)
    public int calculateDisputeRiskScore(UUID customerId, BigDecimal disputeAmount, String category) {
        log.info("Calculating risk score: customerId={}, amount={}, category={}",
                customerId, disputeAmount, category);

        // FIXED: Implement rule-based risk scoring (placeholder for ML)
        try {
            int riskScore = 50; // Base score

            // Factor 1: Customer dispute history
            String historySql = """
                SELECT
                    COUNT(*) as total_disputes,
                    AVG(CASE WHEN resolution_decision = 'RESOLVED_IN_FAVOR_OF_CUSTOMER' THEN 1.0 ELSE 0.0 END) as win_rate
                FROM disputes
                WHERE customer_id = ?
                AND created_at >= CURRENT_DATE - INTERVAL '180 days'
                """;

            Map<String, Object> history = jdbcTemplate.queryForMap(historySql, customerId);
            int totalDisputes = ((Number) history.get("total_disputes")).intValue();
            double winRate = history.get("win_rate") != null ? ((Number) history.get("win_rate")).doubleValue() : 0.5;

            // High dispute count = higher risk
            if (totalDisputes > 10) riskScore += 20;
            else if (totalDisputes > 5) riskScore += 10;

            // Low win rate = higher risk (may be fraudulent disputes)
            if (winRate < 0.3) riskScore += 15;

            // Factor 2: Amount relative to customer's average
            String avgAmountSql = """
                SELECT AVG(amount) as avg_amount
                FROM disputes
                WHERE customer_id = ?
                """;

            BigDecimal avgAmount = jdbcTemplate.queryForObject(avgAmountSql, BigDecimal.class, customerId);
            if (avgAmount != null && disputeAmount.compareTo(avgAmount.multiply(BigDecimal.valueOf(3))) > 0) {
                riskScore += 15; // Unusually large dispute
            }

            // Factor 3: Category fraud rates
            String categoryRateSql = """
                SELECT AVG(CASE WHEN resolution_decision = 'RESOLVED_IN_FAVOR_OF_MERCHANT' THEN 1.0 ELSE 0.0 END) as fraud_rate
                FROM disputes
                WHERE category = ?
                """;

            Double categoryFraudRate = jdbcTemplate.queryForObject(categoryRateSql, Double.class, category);
            if (categoryFraudRate != null && categoryFraudRate > 0.5) {
                riskScore += 10; // High-fraud category
            }

            // Factor 4: Recent dispute frequency (timing pattern)
            String recentSql = """
                SELECT COUNT(*) as recent_count
                FROM disputes
                WHERE customer_id = ?
                AND created_at >= CURRENT_DATE - INTERVAL '30 days'
                """;

            Integer recentCount = jdbcTemplate.queryForObject(recentSql, Integer.class, customerId);
            if (recentCount != null && recentCount > 3) {
                riskScore += 15; // Suspicious frequency
            }

            // Cap risk score at 0-100
            riskScore = Math.max(0, Math.min(100, riskScore));

            log.info("Risk score calculated: {} (history={}, amount={}, category={}, recent={})",
                    riskScore, totalDisputes, disputeAmount, category, recentCount);

            return riskScore;

        } catch (Exception e) {
            log.error("Failed to calculate risk score, returning default", e);
            return 50; // Default medium risk on error
        }
    }
}
