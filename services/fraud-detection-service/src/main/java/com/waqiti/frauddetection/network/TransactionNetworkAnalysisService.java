package com.waqiti.frauddetection.network;

import com.waqiti.frauddetection.repository.TransactionGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Transaction Network Analysis Service - PRODUCTION READY
 *
 * FINAL 5% ENHANCEMENT - Graph-based fraud detection
 *
 * Detects sophisticated fraud patterns:
 * - Money mules (rapid money movement)
 * - Circular transactions (money laundering)
 * - Unusually dense networks
 * - Velocity of new connections
 * - Association with known fraud
 *
 * NOTE: This implementation uses in-memory graph analysis.
 * For production at scale, integrate with Neo4j for persistent graph storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionNetworkAnalysisService {

    private final TransactionGraphRepository graphRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String NETWORK_CACHE_PREFIX = "network:";
    private static final int CACHE_TTL_HOURS = 1;

    /**
     * Analyze transaction network for suspicious patterns
     *
     * @param userId User ID to analyze
     * @param recipientId Recipient ID
     * @param amount Transaction amount
     * @return Risk score from 0.0 (normal) to 1.0 (highly suspicious)
     */
    public double analyzeTransactionNetwork(UUID userId, UUID recipientId, BigDecimal amount) {
        try {
            double riskScore = 0.0;

            // Factor 1: Rapid money movement (20%) - Money mule indicator
            double rapidMovementScore = checkRapidMoneyMovement(userId, recipientId, amount);
            riskScore += rapidMovementScore * 0.2;
            log.debug("NETWORK: Rapid movement score for {}: {}", userId, rapidMovementScore);

            // Factor 2: Circular transactions (30%) - Money laundering indicator
            double circularityScore = detectCircularTransactions(userId, recipientId);
            riskScore += circularityScore * 0.3;
            log.debug("NETWORK: Circularity score for {}: {}", userId, circularityScore);

            // Factor 3: Network density (25%) - Unusual connection patterns
            double densityScore = analyzeNetworkDensity(userId);
            riskScore += densityScore * 0.25;
            log.debug("NETWORK: Density score for {}: {}", userId, densityScore);

            // Factor 4: Velocity of connections (15%) - Rapid account creation pattern
            double velocityScore = analyzeConnectionVelocity(userId);
            riskScore += velocityScore * 0.15;
            log.debug("NETWORK: Velocity score for {}: {}", userId, velocityScore);

            // Factor 5: Known fraud association (10%) - Direct link to fraud
            double associationScore = checkFraudAssociation(userId, recipientId);
            riskScore += associationScore * 0.1;
            log.debug("NETWORK: Association score for {}: {}", userId, associationScore);

            double finalScore = Math.min(1.0, riskScore);
            log.info("NETWORK: Final network risk score for {}: {}", userId, String.format("%.4f", finalScore));

            return finalScore;

        } catch (Exception e) {
            log.error("NETWORK: Error analyzing transaction network for user: {}", userId, e);
            return 0.3; // Moderate risk on error (fail-safe)
        }
    }

    /**
     * Check for rapid money movement patterns (money mule indicator)
     *
     * Pattern: Money comes in → money goes out quickly (< 1 hour)
     */
    @Cacheable(value = "rapid-movement", key = "#userId", unless = "#result == null")
    private double checkRapidMoneyMovement(UUID userId, UUID recipientId, BigDecimal amount) {
        try {
            // Check if user received money recently and is now sending it out
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

            // Get recent incoming transactions
            List<Map<String, Object>> incomingTransactions = graphRepository
                .findRecentIncomingTransactions(userId, oneHourAgo);

            if (incomingTransactions.isEmpty()) {
                return 0.1; // No recent incoming - low risk
            }

            // Check if outgoing amount matches recent incoming (within 20%)
            BigDecimal totalIncoming = incomingTransactions.stream()
                .map(tx -> (BigDecimal) tx.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalIncoming.compareTo(BigDecimal.ZERO) == 0) {
                return 0.1;
            }

            // Calculate how much of incoming money is being sent out
            BigDecimal percentage = amount.divide(totalIncoming, 4, java.math.RoundingMode.HALF_UP);

            // Rapid full passthrough is suspicious
            if (percentage.compareTo(new BigDecimal("0.8")) >= 0) {
                long rapidMovements = graphRepository.countRapidPassthroughPatterns(userId);

                if (rapidMovements == 0) return 0.4; // First time
                if (rapidMovements < 3) return 0.7; // Pattern emerging
                return 1.0; // Clear money mule behavior

            } else if (percentage.compareTo(new BigDecimal("0.5")) >= 0) {
                return 0.5; // Partial passthrough
            }

            return 0.2; // Normal behavior

        } catch (Exception e) {
            log.error("NETWORK: Error checking rapid movement for: {}", userId, e);
            return 0.3;
        }
    }

    /**
     * Detect circular transaction patterns (money laundering indicator)
     *
     * Pattern: A → B → C → A (money returns to origin)
     */
    @Cacheable(value = "circular-tx", key = "#userId", unless = "#result == null")
    private double detectCircularTransactions(UUID userId, UUID recipientId) {
        try {
            // Check if money sent to recipient eventually returns to sender
            // within 2-5 hops (typical laundering depth)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

            long circularPaths = graphRepository.findCircularPaths(userId, 2, 5, thirtyDaysAgo);

            if (circularPaths == 0) return 0.0; // No circular patterns
            if (circularPaths == 1) return 0.5; // One circular path - could be legitimate
            if (circularPaths < 3) return 0.7; // Multiple paths - suspicious
            return 1.0; // Many circular paths - highly suspicious

        } catch (Exception e) {
            log.error("NETWORK: Error detecting circular transactions for: {}", userId, e);
            return 0.2;
        }
    }

    /**
     * Analyze network density (unusually high connections)
     *
     * Pattern: User transacts with too many unique counterparties
     */
    @Cacheable(value = "network-density", key = "#userId", unless = "#result == null")
    private double analyzeNetworkDensity(UUID userId) {
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

            // Count unique transaction partners in last 30 days
            long uniquePartners = graphRepository.countUniquePartners(userId, thirtyDaysAgo);

            // Normal users: < 10 partners/month
            // Active users: 10-50 partners/month
            // Suspicious: 50+ partners/month (possible payment processor/mule)

            if (uniquePartners < 10) return 0.1; // Normal
            if (uniquePartners < 25) return 0.2; // Active user
            if (uniquePartners < 50) return 0.4; // Very active
            if (uniquePartners < 100) return 0.6; // Suspicious
            if (uniquePartners < 200) return 0.8; // Highly suspicious
            return 0.95; // Extremely suspicious (payment processor pattern)

        } catch (Exception e) {
            log.error("NETWORK: Error analyzing network density for: {}", userId, e);
            return 0.2;
        }
    }

    /**
     * Analyze velocity of new connections
     *
     * Pattern: Rapid creation of new transaction relationships
     */
    @Cacheable(value = "connection-velocity", key = "#userId", unless = "#result == null")
    private double analyzeConnectionVelocity(UUID userId) {
        try {
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

            // Count new partners in last 7 days vs previous 23 days
            long recentNewPartners = graphRepository.countNewPartners(userId, sevenDaysAgo);
            long totalNewPartners = graphRepository.countNewPartners(userId, thirtyDaysAgo);

            if (totalNewPartners == 0) return 0.1; // No new connections

            // Calculate velocity (% of new connections in last 7 days)
            double velocity = (double) recentNewPartners / totalNewPartners;

            // High velocity indicates account testing or rapid network building
            if (velocity < 0.3) return 0.1; // Normal growth
            if (velocity < 0.5) return 0.3; // Moderate growth
            if (velocity < 0.7) return 0.6; // High growth
            return 0.9; // Extremely rapid growth - suspicious

        } catch (Exception e) {
            log.error("NETWORK: Error analyzing connection velocity for: {}", userId, e);
            return 0.2;
        }
    }

    /**
     * Check association with known fraud cases
     *
     * Pattern: Direct connection to confirmed fraud accounts
     */
    @Cacheable(value = "fraud-association", key = "#userId + '-' + #recipientId", unless = "#result == null")
    private double checkFraudAssociation(UUID userId, UUID recipientId) {
        try {
            // Check if user or recipient are connected to known fraud cases
            // within 1-3 hops

            // Direct fraud flag (user themselves)
            if (graphRepository.isMarkedAsFraud(userId)) {
                return 1.0; // User is known fraud
            }

            if (graphRepository.isMarkedAsFraud(recipientId)) {
                return 0.95; // Sending to known fraud account
            }

            // 1-hop connection to fraud
            long firstDegreeConnections = graphRepository.countFraudConnections(userId, 1);
            if (firstDegreeConnections > 0) {
                return 0.8; // Direct connection to fraud
            }

            // 2-hop connection to fraud
            long secondDegreeConnections = graphRepository.countFraudConnections(userId, 2);
            if (secondDegreeConnections > 2) {
                return 0.6; // Multiple 2nd-degree fraud connections
            } else if (secondDegreeConnections > 0) {
                return 0.4; // Some 2nd-degree fraud connections
            }

            // 3-hop connection to fraud (weak signal)
            long thirdDegreeConnections = graphRepository.countFraudConnections(userId, 3);
            if (thirdDegreeConnections > 5) {
                return 0.3; // Many distant fraud connections
            }

            return 0.0; // No known fraud associations

        } catch (Exception e) {
            log.error("NETWORK: Error checking fraud association for: {}", userId, e);
            return 0.2;
        }
    }

    /**
     * Calculate network centrality (how "central" a user is in the network)
     *
     * High centrality can indicate payment processors or hubs
     */
    public double calculateNetworkCentrality(UUID userId) {
        try {
            // Simplified centrality: ratio of connections to total transactions
            long totalTransactions = graphRepository.countTotalTransactions(userId);
            long uniquePartners = graphRepository.countUniquePartners(userId, LocalDateTime.now().minusDays(90));

            if (totalTransactions == 0) return 0.0;

            double connectivityRatio = (double) uniquePartners / totalTransactions;

            // High ratio = many unique partners (hub behavior)
            if (connectivityRatio > 0.8) return 0.9; // Almost all transactions to different people
            if (connectivityRatio > 0.5) return 0.6; // High diversity
            if (connectivityRatio > 0.3) return 0.3; // Moderate diversity
            return 0.1; // Normal (repeat transactions)

        } catch (Exception e) {
            log.error("NETWORK: Error calculating centrality for: {}", userId, e);
            return 0.2;
        }
    }

    /**
     * Get network risk assessment with detailed breakdown
     */
    public NetworkRiskAssessment getNetworkRiskAssessment(UUID userId, UUID recipientId, BigDecimal amount) {
        double overallScore = analyzeTransactionNetwork(userId, recipientId, amount);

        return NetworkRiskAssessment.builder()
            .userId(userId)
            .recipientId(recipientId)
            .overallRiskScore(overallScore)
            .rapidMovementScore(checkRapidMoneyMovement(userId, recipientId, amount))
            .circularityScore(detectCircularTransactions(userId, recipientId))
            .densityScore(analyzeNetworkDensity(userId))
            .velocityScore(analyzeConnectionVelocity(userId))
            .fraudAssociationScore(checkFraudAssociation(userId, recipientId))
            .riskLevel(getRiskLevel(overallScore))
            .requiresReview(overallScore > 0.7)
            .shouldBlock(overallScore > 0.9)
            .build();
    }

    private String getRiskLevel(double score) {
        if (score < 0.2) return "LOW";
        if (score < 0.4) return "MODERATE_LOW";
        if (score < 0.6) return "MODERATE";
        if (score < 0.8) return "HIGH";
        return "CRITICAL";
    }

    /**
     * Network Risk Assessment Result
     */
    @lombok.Data
    @lombok.Builder
    public static class NetworkRiskAssessment {
        private UUID userId;
        private UUID recipientId;
        private Double overallRiskScore;
        private Double rapidMovementScore;
        private Double circularityScore;
        private Double densityScore;
        private Double velocityScore;
        private Double fraudAssociationScore;
        private String riskLevel;
        private Boolean requiresReview;
        private Boolean shouldBlock;
    }
}
