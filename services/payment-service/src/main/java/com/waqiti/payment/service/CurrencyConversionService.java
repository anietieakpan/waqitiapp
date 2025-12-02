package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import com.waqiti.payment.dto.MobileMoneyTransferResult;
import org.springframework.stereotype.Service;

/**
 * Currency Conversion Service Interface
 * 
 * Handles currency conversion operations and compliance for cross-border transfers.
 */
@Service
public interface CurrencyConversionService {

    /**
     * Validates currency support for cross-border transfers
     */
    void validateCurrencySupport(String currency, String senderCountry, String receiverCountry);

    /**
     * Performs conversion compliance checks
     */
    void performConversionCompliance(MobileMoneyTransferRequest request, MobileMoneyTransferResult result);
}