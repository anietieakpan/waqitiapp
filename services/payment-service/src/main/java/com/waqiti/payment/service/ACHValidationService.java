package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ACHValidationService {

    public boolean validateRoutingNumber(String routingNumber) {
        log.debug("Validating routing number: {}", routingNumber);
        return routingNumber != null && routingNumber.matches("^\\d{9}$");
    }

    public boolean validateAccountNumber(String accountNumber) {
        log.debug("Validating account number");
        return accountNumber != null && !accountNumber.isEmpty();
    }
}
