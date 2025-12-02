package com.waqiti.kyc.workflow.delegates;

import com.waqiti.notification.dto.NotificationRequest;
import com.waqiti.notification.dto.NotificationType;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.KYCStatus;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import com.waqiti.user.model.User;
import com.waqiti.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("notificationDelegate")
@RequiredArgsConstructor
public class NotificationDelegate implements JavaDelegate {

    private final NotificationService notificationService;
    private final KYCApplicationRepository kycApplicationRepository;
    private final UserRepository userRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Sending KYC notification for process instance: {}", execution.getProcessInstanceId());

        try {
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");
            String kycStatus = (String) execution.getVariable("kycStatus");
            String kycLevel = (String) execution.getVariable("kycLevel");
            Integer riskScore = (Integer) execution.getVariable("riskScore");

            // Get user and application details
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Prepare notification based on status
            NotificationRequest notification = new NotificationRequest();
            notification.setUserId(userId);
            notification.setEmail(user.getEmail());
            notification.setPhone(user.getPhoneNumber());
            
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("firstName", user.getFirstName());
            templateData.put("lastName", user.getLastName());
            templateData.put("applicationId", kycApplicationId);
            templateData.put("kycLevel", kycLevel);

            KYCStatus status = KYCStatus.valueOf(kycStatus);
            
            switch (status) {
                case APPROVED:
                    notification.setType(NotificationType.KYC_APPROVED);
                    notification.setSubject("Your KYC Verification is Complete!");
                    notification.setTemplate("kyc-approved");
                    templateData.put("message", getApprovedMessage(kycLevel));
                    templateData.put("nextSteps", getNextSteps(kycLevel));
                    break;
                    
                case REJECTED:
                    notification.setType(NotificationType.KYC_REJECTED);
                    notification.setSubject("KYC Verification Update");
                    notification.setTemplate("kyc-rejected");
                    templateData.put("rejectionReason", application.getReviewNotes());
                    templateData.put("supportLink", "https://example.com/support/kyc");
                    break;
                    
                case PENDING_REVIEW:
                    notification.setType(NotificationType.KYC_PENDING);
                    notification.setSubject("Your KYC Application is Under Review");
                    notification.setTemplate("kyc-pending-review");
                    templateData.put("estimatedTime", "24-48 hours");
                    break;
                    
                case PENDING_DOCUMENTS:
                    notification.setType(NotificationType.KYC_DOCUMENT_REQUIRED);
                    notification.setSubject("Additional Documents Required for KYC");
                    notification.setTemplate("kyc-documents-required");
                    templateData.put("requiredDocuments", getRequiredDocuments(execution));
                    templateData.put("uploadLink", "https://example.com/kyc/upload/" + kycApplicationId);
                    break;
                    
                default:
                    log.warn("No notification configured for KYC status: {}", status);
                    return;
            }

            notification.setTemplateData(templateData);
            
            // Add push notification data
            Map<String, String> pushData = new HashMap<>();
            pushData.put("type", "kyc_update");
            pushData.put("applicationId", kycApplicationId);
            pushData.put("status", kycStatus);
            notification.setPushData(pushData);

            // Send notification through all channels
            notification.setChannels(new String[]{"email", "sms", "push", "in_app"});
            
            // Send the notification
            notificationService.send(notification);
            
            // Log notification sent
            log.info("KYC notification sent to user: {} for status: {} via channels: {}", 
                    userId, kycStatus, String.join(",", notification.getChannels()));

            // Set process variable
            execution.setVariable("notificationSent", true);
            execution.setVariable("notificationType", notification.getType().toString());

        } catch (Exception e) {
            log.error("Error sending KYC notification", e);
            // Don't fail the process for notification errors
            execution.setVariable("notificationSent", false);
            execution.setVariable("notificationError", e.getMessage());
        }
    }

    private String getApprovedMessage(String kycLevel) {
        switch (kycLevel) {
            case "FULL":
                return "Congratulations! Your identity has been fully verified. You now have access to all Waqiti features including unlimited transactions and international transfers.";
            case "STANDARD":
                return "Your KYC verification is complete! You can now enjoy most Waqiti features with standard transaction limits.";
            case "LIMITED":
                return "Your basic verification is complete. You have limited access to Waqiti services. Complete additional verification steps to unlock more features.";
            default:
                return "Your KYC verification has been processed.";
        }
    }

    private String getNextSteps(String kycLevel) {
        switch (kycLevel) {
            case "FULL":
                return "You can now: Make unlimited P2P transfers, Access international remittance, Apply for Waqiti debit card, Use merchant payment services";
            case "STANDARD":
                return "You can now: Make P2P transfers up to $10,000/month, Access savings features, Use bill payment services";
            case "LIMITED":
                return "You can now: Make P2P transfers up to $1,000/month, Receive payments. To unlock more features, please complete additional verification.";
            default:
                return "Please check your account for available features.";
        }
    }

    private String getRequiredDocuments(DelegateExecution execution) {
        // In a real implementation, this would check which specific documents are missing
        return "Government-issued ID (front and back), Proof of address (utility bill or bank statement dated within 3 months)";
    }
}