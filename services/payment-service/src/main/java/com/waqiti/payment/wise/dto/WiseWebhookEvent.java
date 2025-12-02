package com.waqiti.payment.wise.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Wise Webhook Event DTO
 * 
 * Represents webhook notification events from Wise API.
 */
@Data
public class WiseWebhookEvent {
    
    @JsonProperty("event_type")
    private String eventType;
    
    @JsonProperty("schema_version")
    private String schemaVersion;
    
    @JsonProperty("sent_at")
    private LocalDateTime sentAt;
    
    @JsonProperty("occurred_at")
    private LocalDateTime occurredAt;
    
    private WiseWebhookData data;
    
    @Data
    public static class WiseWebhookData {
        
        // Transfer-related fields
        @JsonProperty("resource")
        private WiseWebhookResource resource;
        
        @JsonProperty("transfer_id")
        private Long transferId;
        
        @JsonProperty("profile_id")
        private Long profileId;
        
        @JsonProperty("account_id")
        private Long accountId;
        
        @JsonProperty("current_status")
        private String currentStatus;
        
        @JsonProperty("previous_status")
        private String previousStatus;
        
        // Balance-related fields
        @JsonProperty("balance_id")
        private Long balanceId;
        
        @JsonProperty("amount")
        private WiseAmount amount;
        
        @JsonProperty("post_transaction_balance_amount")
        private WiseAmount postTransactionBalanceAmount;
        
        // Compliance-related fields
        @JsonProperty("compliance_type")
        private String complianceType;
        
        @JsonProperty("severity")
        private String severity;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("reference_number")
        private String referenceNumber;
        
        // Transaction details
        @JsonProperty("transaction_type")
        private String transactionType;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("exchange_details")
        private WiseExchangeDetails exchangeDetails;
    }
    
    @Data
    public static class WiseWebhookResource {
        private String type;
        private Long id;
        
        @JsonProperty("profile_id")
        private Long profileId;
        
        @JsonProperty("account_id") 
        private Long accountId;
    }
    
    @Data
    public static class WiseAmount {
        private BigDecimal value;
        private String currency;
    }
    
    @Data
    public static class WiseExchangeDetails {
        
        @JsonProperty("from_amount")
        private WiseAmount fromAmount;
        
        @JsonProperty("to_amount")
        private WiseAmount toAmount;
        
        private BigDecimal rate;
        
        @JsonProperty("rate_type")
        private String rateType;
    }
}