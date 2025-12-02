package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutreachService {
    public void scheduleOutreach(String customerId, String outreachType) {
        log.info("Scheduling outreach: customerId={}, type={}", customerId, outreachType);
    }
    public void sendProactiveMessage(String customerId, String message) {
        log.info("Sending proactive message: customerId={}", customerId);
    }
}
