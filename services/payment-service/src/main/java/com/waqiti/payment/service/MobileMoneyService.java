package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import com.waqiti.payment.dto.MobileMoneyTransferResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Mobile Money Service Interface
 * 
 * Provides core mobile money transfer functionality including
 * validation, processing, and settlement operations.
 */
@Service
public interface MobileMoneyService {

    /**
     * Validates a mobile number format and authenticity
     */
    void validateMobileNumber(String mobileNumber, String countryCode);

    /**
     * Validates transfer type compatibility with mobile money provider
     */
    void validateTransferTypeCompatibility(String transferType, String provider);

    /**
     * Validates business hours for mobile money provider operations
     */
    void validateBusinessHours(String provider, String countryCode);

    /**
     * Processes a generic mobile money transfer
     */
    MobileMoneyTransferResult processGenericMobileTransfer(MobileMoneyTransferRequest request);

    /**
     * Performs additional security checks for high-value transactions
     */
    void performAdditionalSecurityChecks(MobileMoneyTransferRequest request);

    /**
     * Finalizes a mobile money transfer
     */
    void finalizeTransfer(MobileMoneyTransferRequest request, MobileMoneyTransferResult result, BigDecimal totalFees);

    /**
     * Creates settlement record for the transfer
     */
    void createSettlementRecord(MobileMoneyTransferRequest request, MobileMoneyTransferResult result, BigDecimal totalFees);

    /**
     * Updates provider statistics
     */
    void updateProviderStatistics(String provider, MobileMoneyTransferRequest request, MobileMoneyTransferResult result);

    /**
     * Generates transaction receipt
     */
    String generateTransactionReceipt(MobileMoneyTransferRequest request, MobileMoneyTransferResult result);
}