package com.waqiti.frauddetection.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a detected fraud ring in the transaction network
 */
@Data
@Builder
public class FraudRing {
    private String ringId;
    private String originUserId;
    private List<String> connectedUsers;
    private int connectionCount;
    private double riskScore;
    private LocalDateTime detectedAt;
    private String ringType; // CIRCULAR, LAYERING, FUNNEL, etc.
    private List<String> suspiciousTransactions;
    
    public boolean isActive() {
        return detectedAt.isAfter(LocalDateTime.now().minusDays(30));
    }
    
    public int getRingSize() {
        return connectedUsers != null ? connectedUsers.size() + 1 : 1;
    }
}