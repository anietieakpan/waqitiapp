package com.waqiti.payment.service;

import com.waqiti.payment.model.AtmLocation;
import com.waqiti.payment.model.AtmTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ATM Service
 * Handles ATM-related operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtmService {

    /**
     * Get ATM location by ATM ID
     */
    public AtmLocation getAtmLocation(String atmId) {
        log.debug("Getting ATM location for ATM ID: {}", atmId);
        // Implementation would fetch from database
        return AtmLocation.builder()
                .atmId(atmId)
                .city("Unknown")
                .country("Unknown")
                .build();
    }

    /**
     * Set ATM location for transaction
     */
    public void setAtmLocation(AtmTransaction transaction, AtmLocation location) {
        transaction.setAtmLocation(location);
    }

    /**
     * Get transaction ID from transaction
     */
    public String getTransactionId(AtmTransaction transaction) {
        return transaction.getTransactionId();
    }

    /**
     * Get ATM ID from transaction
     */
    public String getAtmId(AtmTransaction transaction) {
        return transaction.getAtmId();
    }

    /**
     * Get amount from transaction
     */
    public java.math.BigDecimal getAmount(AtmTransaction transaction) {
        return transaction.getAmount();
    }

    /**
     * Get transaction type from transaction
     */
    public String getTransactionType(AtmTransaction transaction) {
        return transaction.getTransactionType();
    }

    /**
     * Get card number from transaction
     */
    public String getCardNumber(AtmTransaction transaction) {
        return transaction.getCardNumber();
    }

    /**
     * Get transaction time from transaction
     */
    public java.time.LocalDateTime getTransactionTime(AtmTransaction transaction) {
        return transaction.getTransactionTime();
    }

    /**
     * Check if PIN verified
     */
    public Boolean isPinVerified(AtmTransaction transaction) {
        return transaction.getIsPinVerified();
    }

    /**
     * Check if receipt requested
     */
    public Boolean isReceiptRequested(AtmTransaction transaction) {
        return transaction.getIsReceiptRequested();
    }

    /**
     * Get customer ID from transaction
     */
    public String getCustomerId(AtmTransaction transaction) {
        return transaction.getCustomerId();
    }

    /**
     * Get account ID from transaction
     */
    public String getAccountId(AtmTransaction transaction) {
        return transaction.getAccountId();
    }

    /**
     * Get currency from transaction
     */
    public String getCurrency(AtmTransaction transaction) {
        return transaction.getCurrency();
    }

    /**
     * Save ATM transaction
     */
    public void save(AtmTransaction transaction) {
        log.info("Saving ATM transaction: {}", transaction.getTransactionId());
        // Implementation would persist to database
    }
}
