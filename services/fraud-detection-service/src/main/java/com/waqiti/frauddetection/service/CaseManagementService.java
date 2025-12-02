package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Case Management System Integration Service
 *
 * Provides integration with external case management system for:
 * - Manual review case creation
 * - Case status updates
 * - Analyst assignment
 * - Case resolution tracking
 * - Workflow automation
 *
 * @author Waqiti Integration Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CaseManagementService {

    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${case.management.api.url:http://case-management-service/api/v1}")
    private String caseManagementApiUrl;

    @Value("${case.management.enabled:true}")
    private boolean caseManagementEnabled;

    /**
     * Create a review case in the case management system
     */
    public String createReviewCase(FraudCheckRequest request) {
        if (!caseManagementEnabled) {
            log.debug("Case management integration disabled");
            return generateLocalCaseId();
        }

        try {
            log.info("Creating review case in case management system for transaction: {}",
                request.getTransactionId());

            // Build case creation request
            Map<String, Object> caseRequest = buildCaseRequest(request);

            // Call case management API
            String endpoint = caseManagementApiUrl + "/cases";
            Map<String, Object> response = restTemplate.postForObject(
                endpoint, caseRequest, Map.class);

            if (response != null && response.containsKey("caseId")) {
                String caseId = (String) response.get("caseId");
                log.info("Review case created successfully: {}", caseId);

                // Publish case created event
                publishCaseCreatedEvent(caseId, request);

                return caseId;
            } else {
                log.error("Invalid response from case management system");
                return generateLocalCaseId();
            }

        } catch (Exception e) {
            log.error("Failed to create review case in case management system for transaction: {}",
                request.getTransactionId(), e);

            // Fallback: create local case ID
            return generateLocalCaseId();
        }
    }

    /**
     * Update case status
     */
    public void updateCaseStatus(String caseId, String status, String notes) {
        if (!caseManagementEnabled) {
            return;
        }

        try {
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("status", status);
            updateRequest.put("notes", notes);
            updateRequest.put("updatedAt", LocalDateTime.now());

            String endpoint = caseManagementApiUrl + "/cases/" + caseId + "/status";
            restTemplate.put(endpoint, updateRequest);

            log.info("Updated case status for {}: {}", caseId, status);

        } catch (Exception e) {
            log.error("Failed to update case status for {}: {}", caseId, e.getMessage());
        }
    }

    /**
     * Assign case to analyst
     */
    public void assignCase(String caseId, String analystId) {
        if (!caseManagementEnabled) {
            return;
        }

        try {
            Map<String, Object> assignRequest = new HashMap<>();
            assignRequest.put("analystId", analystId);
            assignRequest.put("assignedAt", LocalDateTime.now());

            String endpoint = caseManagementApiUrl + "/cases/" + caseId + "/assign";
            restTemplate.put(endpoint, assignRequest);

            log.info("Assigned case {} to analyst: {}", caseId, analystId);

        } catch (Exception e) {
            log.error("Failed to assign case {}: {}", caseId, e.getMessage());
        }
    }

    /**
     * Resolve case
     */
    public void resolveCase(String caseId, String resolution, String resolvedBy) {
        if (!caseManagementEnabled) {
            return;
        }

        try {
            Map<String, Object> resolveRequest = new HashMap<>();
            resolveRequest.put("resolution", resolution);
            resolveRequest.put("resolvedBy", resolvedBy);
            resolveRequest.put("resolvedAt", LocalDateTime.now());

            String endpoint = caseManagementApiUrl + "/cases/" + caseId + "/resolve";
            restTemplate.put(endpoint, resolveRequest);

            log.info("Resolved case {}: {}", caseId, resolution);

        } catch (Exception e) {
            log.error("Failed to resolve case {}: {}", caseId, e.getMessage());
        }
    }

    /**
     * Build case creation request
     */
    private Map<String, Object> buildCaseRequest(FraudCheckRequest request) {
        Map<String, Object> caseRequest = new HashMap<>();

        caseRequest.put("caseType", "FRAUD_REVIEW");
        caseRequest.put("priority", "HIGH");
        caseRequest.put("transactionId", request.getTransactionId());
        caseRequest.put("userId", request.getUserId());
        caseRequest.put("amount", request.getAmount());
        caseRequest.put("currency", request.getCurrency());
        caseRequest.put("country", request.getCountry());
        caseRequest.put("merchantCategory", request.getMerchantCategory());
        caseRequest.put("deviceFingerprint", request.getDeviceFingerprint());
        caseRequest.put("ipAddress", request.getIpAddress());
        caseRequest.put("createdAt", LocalDateTime.now());

        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "fraud-detection-service");
        metadata.put("automated", true);
        caseRequest.put("metadata", metadata);

        return caseRequest;
    }

    /**
     * Generate local case ID as fallback
     */
    private String generateLocalCaseId() {
        return "REVIEW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Publish case created event
     */
    private void publishCaseCreatedEvent(String caseId, FraudCheckRequest request) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CASE_CREATED");
            event.put("caseId", caseId);
            event.put("transactionId", request.getTransactionId());
            event.put("userId", request.getUserId());
            event.put("createdAt", LocalDateTime.now());

            kafkaTemplate.send("fraud-case-events", caseId, event);

        } catch (Exception e) {
            log.error("Failed to publish case created event: {}", e.getMessage());
        }
    }
}
