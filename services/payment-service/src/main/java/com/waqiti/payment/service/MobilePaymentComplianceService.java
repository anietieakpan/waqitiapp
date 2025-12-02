package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import org.springframework.stereotype.Service;

/**
 * Mobile Payment Compliance Service Interface
 * 
 * Handles mobile payment specific compliance and regulatory requirements.
 */
@Service
public interface MobilePaymentComplianceService {

    /**
     * Performs mobile-specific compliance checks
     */
    void performMobileSpecificCompliance(MobileMoneyTransferRequest request);

    /**
     * Performs cross-border mobile payment compliance
     */
    void performCrossBorderCompliance(MobileMoneyTransferRequest request);
}