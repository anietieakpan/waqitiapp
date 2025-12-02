package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import org.springframework.stereotype.Service;

/**
 * Mobile Payment Fraud Service Interface
 * 
 * Provides mobile payment specific fraud detection and prevention.
 */
@Service
public interface MobilePaymentFraudService {

    /**
     * Performs mobile-specific fraud checks
     */
    void performMobileFraudChecks(MobileMoneyTransferRequest request);

    /**
     * Requires additional verification for high-risk transactions
     */
    void requireAdditionalVerification(MobileMoneyTransferRequest request);
}