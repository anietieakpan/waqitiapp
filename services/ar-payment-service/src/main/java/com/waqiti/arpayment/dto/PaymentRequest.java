package com.waqiti.arpayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for payment requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private UUID requestId;
    private UUID senderId;
    private UUID recipientId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String paymentMethod;
    private String initiatedVia; // AR_GESTURE, AR_VOICE, AR_SCAN, AR_TAP
    private Map<String, Object> metadata;
    private Instant timestamp;
    private SecurityRequirements security;
    private PaymentSchedule schedule;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityRequirements {
        private boolean requiresBiometric;
        private boolean requiresPIN;
        private boolean requires2FA;
        private String authenticationMethod;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSchedule {
        private boolean isRecurring;
        private String frequency; // DAILY, WEEKLY, MONTHLY
        private Instant startDate;
        private Instant endDate;
        private int occurrences;
    }
    
    public static PaymentRequestBuilder builder() {
        return new PaymentRequestBuilder();
    }
    
    public static class PaymentRequestBuilder {
        private UUID requestId = UUID.randomUUID();
        private UUID senderId;
        private UUID recipientId;
        private BigDecimal amount;
        private String currency = "USD";
        private String description;
        private String paymentMethod;
        private String initiatedVia;
        private Map<String, Object> metadata;
        private Instant timestamp = Instant.now();
        private SecurityRequirements security;
        private PaymentSchedule schedule;
        
        public PaymentRequestBuilder senderId(UUID senderId) {
            this.senderId = senderId;
            return this;
        }
        
        public PaymentRequestBuilder recipientId(UUID recipientId) {
            this.recipientId = recipientId;
            return this;
        }
        
        public PaymentRequestBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }
        
        public PaymentRequestBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public PaymentRequestBuilder description(String description) {
            this.description = description;
            return this;
        }
        
        public PaymentRequestBuilder paymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }
        
        public PaymentRequestBuilder initiatedVia(String initiatedVia) {
            this.initiatedVia = initiatedVia;
            return this;
        }
        
        public PaymentRequestBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public PaymentRequestBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public PaymentRequestBuilder security(SecurityRequirements security) {
            this.security = security;
            return this;
        }
        
        public PaymentRequestBuilder schedule(PaymentSchedule schedule) {
            this.schedule = schedule;
            return this;
        }
        
        public PaymentRequest build() {
            return new PaymentRequest(
                requestId,
                senderId,
                recipientId,
                amount,
                currency,
                description,
                paymentMethod,
                initiatedVia,
                metadata,
                timestamp,
                security,
                schedule
            );
        }
    }
}