package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockManagementService {
    @Transactional
    public void blockCustomer(String customerId, String reason) {
        log.warn("Blocking customer: customerId={}, reason={}", customerId, reason);
    }
    @Transactional
    public void unblockCustomer(String customerId) {
        log.info("Unblocking customer: customerId={}", customerId);
    }
    public boolean isBlocked(String customerId) {
        log.debug("Checking block status: customerId={}", customerId);
        return false;
    }
}
