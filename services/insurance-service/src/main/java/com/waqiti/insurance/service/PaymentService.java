package com.waqiti.insurance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    public void processClaimPayment(UUID claimId, BigDecimal amount, String method) {
        log.info("Processing payment: claim={}, amount={}, method={}", claimId, amount, method);
    }
}
