package com.waqiti.recurringpayment.service;

import java.math.BigDecimal;
import java.util.Map;

// Payment Request DTO
public class PaymentRequest {
    private String senderId;
    private String recipientId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String paymentMethod;
    private Map<String, String> metadata;
    
    public static PaymentRequestBuilder builder() {
        return new PaymentRequestBuilder();
    }
    
    // Getters and setters
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    
    // Builder class
    public static class PaymentRequestBuilder {
        private PaymentRequest request = new PaymentRequest();
        
        public PaymentRequestBuilder senderId(String senderId) {
            request.setSenderId(senderId);
            return this;
        }
        
        public PaymentRequestBuilder recipientId(String recipientId) {
            request.setRecipientId(recipientId);
            return this;
        }
        
        public PaymentRequestBuilder amount(BigDecimal amount) {
            request.setAmount(amount);
            return this;
        }
        
        public PaymentRequestBuilder currency(String currency) {
            request.setCurrency(currency);
            return this;
        }
        
        public PaymentRequestBuilder description(String description) {
            request.setDescription(description);
            return this;
        }
        
        public PaymentRequestBuilder paymentMethod(String paymentMethod) {
            request.setPaymentMethod(paymentMethod);
            return this;
        }
        
        public PaymentRequestBuilder metadata(Map<String, String> metadata) {
            request.setMetadata(metadata);
            return this;
        }
        
        public PaymentRequest build() {
            return request;
        }
    }
}
