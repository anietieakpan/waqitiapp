package com.waqiti.ml.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Transaction history service for fraud detection
 * Provides historical transaction data for ML analysis
 */
@Service
@Slf4j
public class TransactionHistoryService {
    
    private final Map<String, List<TransactionRecord>> userTransactions = new ConcurrentHashMap<>();
    private final Map<String, UserRiskHistory> userRiskHistories = new ConcurrentHashMap<>();
    
    public List<TransactionRecord> getTransactionHistory(String userId, int daysBack) {
        List<TransactionRecord> allTransactions = userTransactions.getOrDefault(userId, new ArrayList<>());
        Instant cutoff = Instant.now().minus(daysBack, ChronoUnit.DAYS);
        
        return allTransactions.stream()
            .filter(tx -> tx.getTimestamp().isAfter(cutoff))
            .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
            .collect(Collectors.toList());
    }
    
    public UserRiskHistory getUserRiskHistory(String userId) {
        return userRiskHistories.computeIfAbsent(userId, this::createDefaultRiskHistory);
    }
    
    public void addTransaction(TransactionRecord transaction) {
        String userId = transaction.getUserId();
        userTransactions.computeIfAbsent(userId, k -> new ArrayList<>()).add(transaction);
        
        // Update risk history
        updateRiskHistory(userId, transaction);
        
        log.debug("Added transaction for user {}: amount={}, type={}", 
            userId, transaction.getAmount(), transaction.getType());
    }
    
    private UserRiskHistory createDefaultRiskHistory(String userId) {
        UserRiskHistory history = new UserRiskHistory();
        history.setUserId(userId);
        history.setFailedAttemptsLast24h(0);
        history.setChargebacksInLast90Days(false);
        history.setDisputesInLast90Days(false);
        history.setHistoricalFraudRate(0.0);
        history.setLastUpdated(LocalDateTime.now());
        
        return history;
    }
    
    private void updateRiskHistory(String userId, TransactionRecord transaction) {
        UserRiskHistory history = getUserRiskHistory(userId);
        
        // Count failed attempts in last 24 hours
        Instant last24h = Instant.now().minus(24, ChronoUnit.HOURS);
        List<TransactionRecord> recent = getTransactionHistory(userId, 1);
        long failedCount = recent.stream()
            .filter(tx -> tx.getTimestamp().isAfter(last24h))
            .filter(tx -> "FAILED".equals(tx.getStatus()))
            .count();
        
        history.setFailedAttemptsLast24h((int) failedCount);
        history.setLastUpdated(LocalDateTime.now());
        
        // Update fraud rate based on historical data
        updateHistoricalFraudRate(userId, history);
    }
    
    private void updateHistoricalFraudRate(String userId, UserRiskHistory history) {
        List<TransactionRecord> allTransactions = userTransactions.getOrDefault(userId, new ArrayList<>());
        
        if (allTransactions.isEmpty()) {
            history.setHistoricalFraudRate(0.0);
            return;
        }
        
        long fraudulentCount = allTransactions.stream()
            .filter(tx -> "FRAUDULENT".equals(tx.getStatus()))
            .count();
        
        double fraudRate = (double) fraudulentCount / allTransactions.size();
        history.setHistoricalFraudRate(fraudRate);
    }
    
    // Data classes
    public static class TransactionRecord {
        private String transactionId;
        private String userId;
        private BigDecimal amount;
        private String currency;
        private String type;
        private String status;
        private Instant timestamp;
        private String merchantId;
        private String location;
        
        public TransactionRecord() {}
        
        public TransactionRecord(String transactionId, String userId, BigDecimal amount, String type) {
            this.transactionId = transactionId;
            this.userId = userId;
            this.amount = amount;
            this.type = type;
            this.timestamp = Instant.now();
            this.status = "PENDING";
        }
        
        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
    }
    
    public static class UserRiskHistory {
        private String userId;
        private int failedAttemptsLast24h;
        private boolean chargebacksInLast90Days;
        private boolean disputesInLast90Days;
        private double historicalFraudRate;
        private LocalDateTime lastUpdated;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public int getFailedAttemptsLast24h() { return failedAttemptsLast24h; }
        public void setFailedAttemptsLast24h(int failedAttemptsLast24h) { this.failedAttemptsLast24h = failedAttemptsLast24h; }
        
        public boolean hasChargebacksInLast90Days() { return chargebacksInLast90Days; }
        public void setChargebacksInLast90Days(boolean chargebacksInLast90Days) { this.chargebacksInLast90Days = chargebacksInLast90Days; }
        
        public boolean hasDisputesInLast90Days() { return disputesInLast90Days; }
        public void setDisputesInLast90Days(boolean disputesInLast90Days) { this.disputesInLast90Days = disputesInLast90Days; }
        
        public double getHistoricalFraudRate() { return historicalFraudRate; }
        public void setHistoricalFraudRate(double historicalFraudRate) { this.historicalFraudRate = historicalFraudRate; }
        
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    }
}