package com.waqiti.customer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceRequestService {
    public String createServiceRequest(String customerId, String requestType) {
        log.info("Creating service request: customerId={}, type={}", customerId, requestType);
        return "SR-" + System.currentTimeMillis();
    }
    public List<String> getActiveRequests(String customerId) {
        log.info("Getting active service requests: customerId={}", customerId);
        return Collections.emptyList();
    }
    public void completeServiceRequest(String requestId) {
        log.info("Completing service request: requestId={}", requestId);
    }
}
