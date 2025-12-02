package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Mobile Payment Fee Service Interface
 * 
 * Calculates fees for mobile money transfers including network and regulatory fees.
 */
@Service
public interface MobilePaymentFeeService {

    /**
     * Calculates transfer fee for mobile money transfer
     */
    BigDecimal calculateTransferFee(MobileMoneyTransferRequest request);

    /**
     * Calculates network fee
     */
    BigDecimal calculateNetworkFee(MobileMoneyTransferRequest request);

    /**
     * Calculates regulatory fee
     */
    BigDecimal calculateRegulatoryFee(MobileMoneyTransferRequest request);

    /**
     * Calculates cross-border transfer fee
     */
    BigDecimal calculateCrossBorderFee(MobileMoneyTransferRequest request);
}