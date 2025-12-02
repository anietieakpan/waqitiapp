package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Event fired when a compliance check is performed
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ComplianceCheckEvent extends FinancialEvent {
    
    private String checkId;
    private String transactionId;
    private String checkType;
    private String checkResult;
    private String riskLevel;
    private Map<String, Object> checkDetails;
    
    @Override
    public String getEventType() {
        return "COMPLIANCE_CHECK";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "checkId", checkId,
            "transactionId", transactionId,
            "checkType", checkType,
            "checkResult", checkResult,
            "riskLevel", riskLevel,
            "checkDetails", checkDetails != null ? checkDetails : Map.of()
        );
    }
}