package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerMigrationService {
    public void migrateCustomerData(String customerId, String sourceSystem) {
        log.info("Migrating customer data: customerId={}, source={}", customerId, sourceSystem);
    }
    public void validateMigration(String customerId) {
        log.info("Validating migration: customerId={}", customerId);
    }
}
