package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Mobile Payment Limit Service Interface
 * 
 * Manages transaction limits for mobile payments and cross-border transfers.
 */
@Service
public interface MobilePaymentLimitService {

    /**
     * Validates transaction limits for mobile money transfer
     */
    void validateTransactionLimits(MobileMoneyTransferRequest request);

    /**
     * Updates cross-border transaction limits
     */
    void updateCrossBorderLimits(String userId, BigDecimal amount);

    /**
     * Updates user transaction limits
     */
    void updateUserLimits(String userId, BigDecimal amount);
}