package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Event fired when fraud is detected
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class FraudDetectedEvent extends FinancialEvent {
    
    private String fraudId;
    private String transactionId;
    private String fraudType;
    private Double riskScore;
    private String riskLevel;
    private Map<String, Object> fraudIndicators;
    private String actionTaken;
    
    @Override
    public String getEventType() {
        return "FRAUD_DETECTED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "fraudId", fraudId,
            "transactionId", transactionId,
            "fraudType", fraudType,
            "riskScore", riskScore,
            "riskLevel", riskLevel,
            "fraudIndicators", fraudIndicators != null ? fraudIndicators : Map.of(),
            "actionTaken", actionTaken
        );
    }
}