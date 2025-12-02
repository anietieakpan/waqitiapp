package com.waqiti.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserNotificationService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void sendVerificationFailureNotification(String userId, String verificationType) {
        log.info("Sending verification failure notification for user: {}, type: {}", userId, verificationType);
        
        kafkaTemplate.send("user-notifications", Map.of(
            "userId", userId,
            "notificationType", "VERIFICATION_FAILURE",
            "verificationType", verificationType,
            "timestamp", Instant.now().toString(),
            "priority", "HIGH"
        ));
    }
    
    public void sendDocumentVerificationSuccess(String userId, String documentType) {
        log.info("Sending document verification success notification for user: {}, type: {}", userId, documentType);
        
        kafkaTemplate.send("user-notifications", Map.of(
            "userId", userId,
            "notificationType", "DOCUMENT_VERIFICATION_SUCCESS",
            "documentType", documentType,
            "timestamp", Instant.now().toString(),
            "priority", "NORMAL"
        ));
    }
    
    public void sendDocumentVerificationFailure(String userId, String documentType, String failureReason) {
        log.info("Sending document verification failure notification for user: {}, type: {}", userId, documentType);
        
        kafkaTemplate.send("user-notifications", Map.of(
            "userId", userId,
            "notificationType", "DOCUMENT_VERIFICATION_FAILURE",
            "documentType", documentType,
            "failureReason", failureReason != null ? failureReason : "Unknown",
            "timestamp", Instant.now().toString(),
            "priority", "HIGH"
        ));
    }
}