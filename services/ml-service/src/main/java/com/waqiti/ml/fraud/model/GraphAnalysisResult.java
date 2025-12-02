package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of graph-based fraud analysis.
 * Analyzes transaction networks and relationships.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphAnalysisResult {

    private LocalDateTime timestamp;

    // Graph scores
    private Double communityRiskScore; // Risk based on transaction network
    private Double connectionRiskScore; // Risk based on connected entities
    private Double overallGraphScore; // Combined graph-based risk score

    // Network metrics
    private Integer connectedAccounts; // Number of connected accounts
    private Integer sharedDevices; // Devices shared with other accounts
    private Integer sharedIpAddresses; // IPs shared with other accounts
    private Integer transactionRingSize; // Size of transaction ring if detected

    // Fraud ring detection
    private Boolean fraudRingDetected;
    private String fraudRingId;
    private List<String> ringMembers;
    private Double ringRiskScore;

    // Network patterns
    private Boolean cyclicTransactionsDetected; // Money flowing in circles
    private Boolean fanOutPatternDetected; // One account sending to many
    private Boolean fanInPatternDetected; // Many accounts sending to one
    private Boolean layeringDetected; // Multiple transaction layers

    // Connected entity analysis
    private Integer highRiskConnections; // Connections to known fraud accounts
    private Integer suspiciousConnections; // Connections to suspicious accounts
    private List<String> connectedFraudsters; // Known fraudsters in network

    // Graph centrality metrics
    private Double pageRankScore; // Influence in transaction network
    private Double betweennessCentrality; // Position in network
    private Double clusteringCoefficient; // How clustered connections are

    // Money flow analysis
    private Boolean unusualMoneyFlow; // Atypical money movement patterns
    private Integer transactionHops; // Degrees of separation from source
    private List<String> suspiciousPathways; // Suspicious transaction paths

    /**
     * Check if graph analysis indicates fraud ring
     */
    public boolean indicatesFraudRing() {
        return Boolean.TRUE.equals(fraudRingDetected) || 
               (ringRiskScore != null && ringRiskScore > 0.7);
    }

    /**
     * Check if high-risk network detected
     */
    public boolean isHighRiskNetwork() {
        return overallGraphScore != null && overallGraphScore > 0.7;
    }
}
