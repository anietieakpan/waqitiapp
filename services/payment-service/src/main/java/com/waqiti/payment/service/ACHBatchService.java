package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ACHBatchService {

    public void addToBatch(String achTransactionId) {
        log.info("Adding ACH transaction to batch: {}", achTransactionId);
        // Implementation stub
    }
}
