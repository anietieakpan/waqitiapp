package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationPreferenceService {
    public void updatePreferences(String customerId, Map<String, Object> preferences) {
        log.info("Updating communication preferences: customerId={}", customerId);
    }
    public Map<String, Object> getPreferences(String customerId) {
        log.info("Getting communication preferences: customerId={}", customerId);
        return Map.of("preferredChannel", "EMAIL", "frequency", "WEEKLY");
    }
}
