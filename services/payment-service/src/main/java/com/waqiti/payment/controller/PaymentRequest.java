package com.waqiti.payment.controller;

import java.util.UUID; /**
 * Example DTOs (these would normally be in separate files)
 */
public class PaymentRequest {
    private UUID sourceWalletId;
    private UUID targetWalletId;
    private java.math.BigDecimal amount;
    private String currency;
    private String description;
    
    // Getters and setters
    public UUID getSourceWalletId() { return sourceWalletId; }
    public void setSourceWalletId(UUID sourceWalletId) { this.sourceWalletId = sourceWalletId; }
    public UUID getTargetWalletId() { return targetWalletId; }
    public void setTargetWalletId(UUID targetWalletId) { this.targetWalletId = targetWalletId; }
    public java.math.BigDecimal getAmount() { return amount; }
    public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
