package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import com.waqiti.payment.dto.MobileMoneyTransferResult;
import org.springframework.stereotype.Service;

/**
 * Cross-Border Mobile Payment Service Interface
 * 
 * Handles cross-border mobile payment processing including
 * regulatory compliance and international transfer management.
 */
@Service
public interface CrossBorderMobilePaymentService {

    /**
     * Processes cross-border mobile money transfer
     */
    MobileMoneyTransferResult processCrossBorderTransfer(MobileMoneyTransferRequest request);

    /**
     * Performs regulatory reporting for cross-border transfers
     */
    void performRegulatoryReporting(MobileMoneyTransferRequest request, MobileMoneyTransferResult result);
}