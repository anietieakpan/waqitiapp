package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerOnboardingService {
    public void startOnboarding(String customerId) {
        log.info("Starting onboarding: customerId={}", customerId);
    }
    public Map<String, Object> getOnboardingStatus(String customerId) {
        log.info("Getting onboarding status: customerId={}", customerId);
        return Map.of("status", "IN_PROGRESS", "completionPercentage", 50);
    }
    public void completeOnboardingStep(String customerId, String step) {
        log.info("Completing onboarding step: customerId={}, step={}", customerId, step);
    }
}
