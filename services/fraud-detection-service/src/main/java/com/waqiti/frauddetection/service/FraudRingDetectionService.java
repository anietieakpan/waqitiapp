package com.waqiti.frauddetection.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Fraud Ring Detection Service
 *
 * High-level service for detecting organized fraud rings using graph analysis.
 * Wraps Neo4jTransactionGraphService with fraud-specific logic.
 *
 * PRODUCTION-GRADE IMPLEMENTATION
 * - Community-based fraud detection
 * - Network analysis
 * - Centrality scoring
 * - Risk assessment
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FraudRingDetectionService {

    private final Neo4jTransactionGraphService graphService;

    /**
     * Analyze user for fraud ring membership
     */
    public Neo4jTransactionGraphService.FraudRingAnalysis analyzeFraudRing(UUID userId) {
        log.debug("Analyzing fraud ring for user: {}", userId);

        try {
            return graphService.detectFraudRing(userId);
        } catch (Exception e) {
            log.error("Error analyzing fraud ring for user: {}", userId, e);
            // Return safe default on error
            return Neo4jTransactionGraphService.FraudRingAnalysis.builder()
                .userId(userId)
                .communitySize(0)
                .fraudsterCount(0)
                .fraudsterRatio(0.0)
                .isFraudRing(false)
                .riskScore(0.5) // Conservative default
                .centrality(0.5)
                .build();
        }
    }

    /**
     * Get network centrality for user
     */
    public double getNetworkCentrality(UUID userId) {
        try {
            return graphService.calculateNetworkCentrality(userId);
        } catch (Exception e) {
            log.error("Error getting network centrality for user: {}", userId, e);
            return 0.5; // Conservative default
        }
    }

    /**
     * Get connection diversity for user
     */
    public double getConnectionDiversity(UUID userId) {
        try {
            return graphService.calculateConnectionDiversity(userId);
        } catch (Exception e) {
            log.error("Error getting connection diversity for user: {}", userId, e);
            return 0.5; // Conservative default
        }
    }

    /**
     * Count suspicious connections
     */
    public double getSuspiciousConnectionRatio(UUID userId) {
        try {
            return graphService.countSuspiciousConnections(userId);
        } catch (Exception e) {
            log.error("Error counting suspicious connections for user: {}", userId, e);
            return 0.0;
        }
    }
}
