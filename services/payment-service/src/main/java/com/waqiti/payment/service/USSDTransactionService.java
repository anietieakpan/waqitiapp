package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import com.waqiti.payment.dto.MobileMoneyTransferResult;
import org.springframework.stereotype.Service;

/**
 * USSD Transaction Service Interface
 * 
 * Handles USSD-based mobile money transactions and session management.
 */
@Service
public interface USSDTransactionService {

    /**
     * Validates USSD session for mobile money transfer
     */
    void validateUSSDSession(String ussdCode, String mobileNumber);

    /**
     * Processes USSD-based mobile money transfer
     */
    MobileMoneyTransferResult processUSSDTransfer(MobileMoneyTransferRequest request);
}