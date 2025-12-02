package com.waqiti.kyc.service.impl;

import com.waqiti.kyc.service.NotificationService;
import com.waqiti.common.notification.NotificationClient;
import com.waqiti.common.notification.NotificationRequest;
import com.waqiti.common.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of NotificationService for KYC notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    
    private final NotificationClient notificationClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kyc.notifications.topic:kyc-notifications}")
    private String notificationTopic;
    
    @Value("${kyc.notifications.ops-email:ops@example.com}")
    private String opsEmail;
    
    @Override
    public void sendVerificationReminder(String userId, String verificationId) {
        log.info("Sending verification reminder to user: {}", userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("verificationId", verificationId);
        data.put("message", "Your KYC verification is pending. Please complete it to access all features.");
        data.put("actionUrl", "/kyc/verification/" + verificationId);
        
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.KYC_REMINDER)
                .title("Complete Your KYC Verification")
                .message("Your identity verification is waiting for completion")
                .data(data)
                .priority("HIGH")
                .build();
        
        sendNotification(request);
    }
    
    @Override
    public void sendVerificationCompleted(String userId, String verificationId, String status) {
        log.info("Sending verification completed notification to user: {}, status: {}", userId, status);
        
        String title;
        String message;
        NotificationType type;
        
        switch (status.toUpperCase()) {
            case "VERIFIED":
                title = "KYC Verification Successful";
                message = "Your identity has been verified successfully!";
                type = NotificationType.KYC_APPROVED;
                break;
            case "REJECTED":
                title = "KYC Verification Failed";
                message = "Unfortunately, we couldn't verify your identity. Please try again.";
                type = NotificationType.KYC_REJECTED;
                break;
            default:
                title = "KYC Verification Update";
                message = "Your verification status has been updated.";
                type = NotificationType.KYC_UPDATE;
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("verificationId", verificationId);
        data.put("status", status);
        data.put("completedAt", LocalDateTime.now().toString());
        
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .data(data)
                .priority("HIGH")
                .build();
        
        sendNotification(request);
    }
    
    @Override
    public void sendDocumentUploadConfirmation(String userId, String documentType) {
        log.info("Sending document upload confirmation to user: {}", userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("documentType", documentType);
        data.put("uploadedAt", LocalDateTime.now().toString());
        
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.DOCUMENT_UPLOADED)
                .title("Document Uploaded Successfully")
                .message("Your " + documentType + " has been uploaded and is being reviewed")
                .data(data)
                .priority("MEDIUM")
                .build();
        
        sendNotification(request);
    }
    
    @Override
    public void sendDocumentRejected(String userId, String documentType, String reason) {
        log.info("Sending document rejected notification to user: {}", userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("documentType", documentType);
        data.put("reason", reason);
        data.put("actionUrl", "/kyc/upload-documents");
        
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.DOCUMENT_REJECTED)
                .title("Document Rejected")
                .message("Your " + documentType + " was rejected. Please upload a new one.")
                .data(data)
                .priority("HIGH")
                .build();
        
        sendNotification(request);
    }
    
    @Override
    public void sendManualReviewRequired(String userId, String verificationId, String reason) {
        log.info("Sending manual review notification to user: {}", userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("verificationId", verificationId);
        data.put("reason", reason);
        
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.KYC_MANUAL_REVIEW)
                .title("Additional Review Required")
                .message("Your verification requires additional review. We'll update you soon.")
                .data(data)
                .priority("MEDIUM")
                .build();
        
        sendNotification(request);
    }
    
    @Override
    public void sendProviderHealthAlert(String providerName, boolean isHealthy) {
        log.info("Sending provider health alert for: {}, healthy: {}", providerName, isHealthy);
        
        Map<String, Object> alert = new HashMap<>();
        alert.put("providerName", providerName);
        alert.put("isHealthy", isHealthy);
        alert.put("timestamp", LocalDateTime.now().toString());
        alert.put("severity", isHealthy ? "INFO" : "CRITICAL");
        
        // Send to operations team
        NotificationRequest request = NotificationRequest.builder()
                .recipient(opsEmail)
                .type(NotificationType.SYSTEM_ALERT)
                .title("KYC Provider Health Alert: " + providerName)
                .message(providerName + " is " + (isHealthy ? "healthy" : "UNHEALTHY"))
                .data(alert)
                .priority("URGENT")
                .build();
        
        sendNotification(request);
        
        // Also publish to monitoring topic
        kafkaTemplate.send("system-alerts", alert);
    }
    
    @Override
    public void sendDailyStatsReport(Map<String, Object> stats) {
        log.info("Sending daily stats report");
        
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("Daily KYC Statistics Report\n");
        reportBuilder.append("==========================\n\n");
        
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            reportBuilder.append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append("\n");
        }
        
        NotificationRequest request = NotificationRequest.builder()
                .recipient(opsEmail)
                .type(NotificationType.DAILY_REPORT)
                .title("Daily KYC Statistics - " + LocalDateTime.now().toLocalDate())
                .message(reportBuilder.toString())
                .data(stats)
                .priority("LOW")
                .build();
        
        sendNotification(request);
    }
    
    @Override
    public void sendVerificationExpired(String userId, String verificationId) {
        log.info("Sending verification expired notification to user: {}", userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("verificationId", verificationId);
        data.put("actionUrl", "/kyc/start-verification");
        
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.KYC_EXPIRED)
                .title("Verification Expired")
                .message("Your KYC verification has expired. Please start a new verification.")
                .data(data)
                .priority("HIGH")
                .build();
        
        sendNotification(request);
    }
    
    @Override
    public void sendAdditionalDocumentsRequired(String userId, String verificationId, 
                                               List<String> requiredDocuments) {
        log.info("Sending additional documents required notification to user: {}", userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("verificationId", verificationId);
        data.put("requiredDocuments", requiredDocuments);
        data.put("actionUrl", "/kyc/upload-documents/" + verificationId);
        
        String docList = String.join(", ", requiredDocuments);
        
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.DOCUMENTS_REQUIRED)
                .title("Additional Documents Required")
                .message("Please upload the following documents: " + docList)
                .data(data)
                .priority("HIGH")
                .build();
        
        sendNotification(request);
    }
    
    @Override
    public void sendVerificationStarted(String userId, String verificationId) {
        log.info("Sending verification started notification to user: {}", userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("verificationId", verificationId);
        data.put("startedAt", LocalDateTime.now().toString());
        
        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.KYC_STARTED)
                .title("Verification Started")
                .message("Your identity verification has started. We'll notify you once it's complete.")
                .data(data)
                .priority("MEDIUM")
                .build();
        
        sendNotification(request);
    }
    
    private void sendNotification(NotificationRequest request) {
        try {
            // Send via notification client
            notificationClient.send(request);
            
            // Also publish to Kafka for async processing
            kafkaTemplate.send(notificationTopic, request);
            
            log.debug("Notification sent successfully: {}", request.getType());
            
        } catch (Exception e) {
            log.error("Failed to send notification", e);
            // Don't throw exception to avoid disrupting the main flow
        }
    }
}