package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeathNotificationService {
    @Transactional
    public void processDeathNotification(String customerId, String deathCertificateId) {
        log.warn("Processing death notification: customerId={}", customerId);
    }
    public void freezeAccounts(String customerId) {
        log.warn("Freezing accounts for deceased customer: customerId={}", customerId);
    }
    public void notifyBeneficiaries(String customerId) {
        log.info("Notifying beneficiaries: customerId={}", customerId);
    }
}
