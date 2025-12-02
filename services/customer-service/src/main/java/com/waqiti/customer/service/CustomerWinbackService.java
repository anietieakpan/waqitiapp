package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerWinbackService {
    public void createWinbackCampaign(String customerId) {
        log.info("Creating winback campaign: customerId={}", customerId);
    }
    public void trackWinbackSuccess(String customerId) {
        log.info("Tracking winback success: customerId={}", customerId);
    }
}
