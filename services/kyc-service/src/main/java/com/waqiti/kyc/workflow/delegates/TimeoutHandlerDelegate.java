package com.waqiti.kyc.workflow.delegates;

import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.KYCStatus;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import com.waqiti.notification.dto.NotificationRequest;
import com.waqiti.notification.dto.NotificationType;
import com.waqiti.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("timeoutHandlerDelegate")
@RequiredArgsConstructor
public class TimeoutHandlerDelegate implements JavaDelegate {

    private final KYCApplicationRepository kycApplicationRepository;
    private final NotificationService notificationService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Handling timeout for process instance: {}", execution.getProcessInstanceId());

        try {
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");

            // Get KYC application
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Update application status to timeout
            application.setStatus(KYCStatus.TIMEOUT);
            application.setLastUpdated(LocalDateTime.now());
            
            // Add timeout reason
            String timeoutReason = determineTimeoutReason(execution);
            application.setReviewNotes("Process timeout: " + timeoutReason);
            
            kycApplicationRepository.save(application);

            // Send timeout notification to user
            sendTimeoutNotification(userId, application, timeoutReason);

            // Send alert to operations team
            sendOperationsAlert(application, timeoutReason);

            // Set process variables
            execution.setVariable("kycStatus", KYCStatus.TIMEOUT.toString());
            execution.setVariable("timeoutReason", timeoutReason);
            execution.setVariable("timeoutHandled", true);
            execution.setVariable("timeoutHandledAt", LocalDateTime.now());

            // Schedule retry if applicable
            if (shouldScheduleRetry(execution)) {
                execution.setVariable("scheduleRetry", true);
                execution.setVariable("retryAt", LocalDateTime.now().plusHours(24));
            }

            log.info("Timeout handled for KYC application: {} with reason: {}", kycApplicationId, timeoutReason);

        } catch (Exception e) {
            log.error("Error handling timeout", e);
            
            // Set error variables
            execution.setVariable("timeoutHandled", false);
            execution.setVariable("timeoutError", e.getMessage());
            
            throw e;
        }
    }

    private String determineTimeoutReason(DelegateExecution execution) {
        String currentActivity = execution.getCurrentActivityName();
        
        switch (currentActivity) {
            case "Identity Verification":
                return "Identity verification provider took too long to respond";
            case "Document Verification":
                return "Document verification process exceeded time limit";
            case "Selfie Verification":
                return "Selfie verification and liveness check timed out";
            case "Address Verification":
                return "Address verification process timed out";
            case "AML Screening":
                return "AML screening process exceeded maximum processing time";
            case "Manual KYC Review":
                return "Manual review task was not completed within the required timeframe";
            default:
                return "KYC verification process exceeded maximum processing time of 30 minutes";
        }
    }

    private void sendTimeoutNotification(String userId, KYCApplication application, String reason) {
        try {
            NotificationRequest notification = new NotificationRequest();
            notification.setUserId(userId);
            notification.setType(NotificationType.KYC_TIMEOUT);
            notification.setSubject("KYC Verification Timeout");
            notification.setTemplate("kyc-timeout");
            
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("applicationId", application.getId());
            templateData.put("reason", reason);
            templateData.put("supportEmail", "support@example.com");
            templateData.put("supportPhone", "+1-800-WAQITI");
            
            notification.setTemplateData(templateData);
            notification.setChannels(new String[]{"email", "sms", "push", "in_app"});
            
            notificationService.send(notification);
            
        } catch (Exception e) {
            log.error("Failed to send timeout notification to user: {}", userId, e);
        }
    }

    private void sendOperationsAlert(KYCApplication application, String reason) {
        try {
            NotificationRequest alert = new NotificationRequest();
            alert.setUserId("ops-team");
            alert.setType(NotificationType.SYSTEM_ALERT);
            alert.setSubject("KYC Process Timeout Alert");
            alert.setTemplate("kyc-timeout-alert");
            
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("applicationId", application.getId());
            templateData.put("userId", application.getUserId());
            templateData.put("reason", reason);
            templateData.put("processInstanceId", application.getProcessInstanceId());
            templateData.put("createdAt", application.getCreatedAt());
            templateData.put("provider", "Multiple providers may be affected");
            
            alert.setTemplateData(templateData);
            alert.setChannels(new String[]{"email", "slack"});
            
            notificationService.send(alert);
            
        } catch (Exception e) {
            log.error("Failed to send operations alert for timeout", e);
        }
    }

    private boolean shouldScheduleRetry(DelegateExecution execution) {
        // Check if this is the first timeout (not a retry)
        Integer retryCount = (Integer) execution.getVariable("retryCount");
        return retryCount == null || retryCount < 2;
    }
}