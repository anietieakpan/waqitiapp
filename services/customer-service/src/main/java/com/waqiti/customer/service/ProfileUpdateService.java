package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileUpdateService {
    @Transactional
    public void updateProfile(String customerId, Map<String, Object> updates) {
        log.info("Updating customer profile: customerId={}", customerId);
    }
    public void updateContactInfo(String customerId, String contactType, String value) {
        log.info("Updating contact info: customerId={}, type={}", customerId, contactType);
    }
    public void updateAddress(String customerId, Map<String, String> address) {
        log.info("Updating address: customerId={}", customerId);
    }
}
