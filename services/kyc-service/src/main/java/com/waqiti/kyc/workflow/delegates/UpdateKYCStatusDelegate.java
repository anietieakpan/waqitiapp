package com.waqiti.kyc.workflow.delegates;

import com.waqiti.common.events.KYCCompletedEvent;
import com.waqiti.common.events.KYCRejectedEvent;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.KYCStatus;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import com.waqiti.user.model.User;
import com.waqiti.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("updateKYCStatusDelegate")
@RequiredArgsConstructor
public class UpdateKYCStatusDelegate implements JavaDelegate {

    private final KYCApplicationRepository kycApplicationRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Updating KYC status for process instance: {}", execution.getProcessInstanceId());

        try {
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");
            Integer riskScore = (Integer) execution.getVariable("riskScore");
            
            // Get KYC application
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Determine final KYC status based on risk score
            KYCStatus finalStatus;
            String kycLevel;
            
            if (riskScore < 30) {
                finalStatus = KYCStatus.APPROVED;
                kycLevel = "FULL";
            } else if (riskScore < 50) {
                finalStatus = KYCStatus.APPROVED;
                kycLevel = "STANDARD";
            } else if (riskScore < 70) {
                finalStatus = KYCStatus.PENDING_REVIEW;
                kycLevel = "LIMITED";
            } else {
                finalStatus = KYCStatus.REJECTED;
                kycLevel = "NONE";
            }

            // Check if manual review overrode the decision
            String reviewDecision = (String) execution.getVariable("reviewDecision");
            if (reviewDecision != null) {
                switch (reviewDecision) {
                    case "approve":
                        finalStatus = KYCStatus.APPROVED;
                        kycLevel = "STANDARD";
                        break;
                    case "reject":
                        finalStatus = KYCStatus.REJECTED;
                        kycLevel = "NONE";
                        break;
                    case "request_info":
                        finalStatus = KYCStatus.PENDING_DOCUMENTS;
                        kycLevel = "LIMITED";
                        break;
                }
            }

            // Update application
            application.setStatus(finalStatus);
            application.setOverallRiskScore(riskScore);
            application.setCompletedAt(LocalDateTime.now());
            application.setKycLevel(kycLevel);
            
            // Add review notes if available
            String reviewNotes = (String) execution.getVariable("reviewNotes");
            if (reviewNotes != null) {
                application.setReviewNotes(reviewNotes);
                application.setReviewedBy((String) execution.getVariable("kycReviewer"));
                application.setReviewedAt(LocalDateTime.now());
            }

            // Calculate processing time
            long processingTimeMinutes = java.time.Duration.between(
                    application.getCreatedAt(), 
                    LocalDateTime.now()
            ).toMinutes();
            application.setProcessingTimeMinutes(processingTimeMinutes);

            kycApplicationRepository.save(application);

            // Update user KYC status
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            user.setKycStatus(finalStatus.toString());
            user.setKycLevel(kycLevel);
            user.setKycVerifiedAt(LocalDateTime.now());
            userRepository.save(user);

            // Prepare event data
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("userId", userId);
            eventData.put("applicationId", kycApplicationId);
            eventData.put("status", finalStatus.toString());
            eventData.put("kycLevel", kycLevel);
            eventData.put("riskScore", riskScore);
            eventData.put("processingTimeMinutes", processingTimeMinutes);
            eventData.put("timestamp", LocalDateTime.now());

            // Send appropriate event
            if (finalStatus == KYCStatus.APPROVED) {
                KYCCompletedEvent event = KYCCompletedEvent.builder()
                        .userId(userId)
                        .applicationId(kycApplicationId)
                        .kycLevel(kycLevel)
                        .verifiedAt(LocalDateTime.now())
                        .metadata(eventData)
                        .build();
                
                kafkaTemplate.send("kyc-completed", event);
                log.info("Sent KYC completed event for user: {} with level: {}", userId, kycLevel);
                
            } else if (finalStatus == KYCStatus.REJECTED) {
                KYCRejectedEvent event = KYCRejectedEvent.builder()
                        .userId(userId)
                        .applicationId(kycApplicationId)
                        .reason(determineRejectionReason(execution))
                        .rejectedAt(LocalDateTime.now())
                        .metadata(eventData)
                        .build();
                
                kafkaTemplate.send("kyc-rejected", event);
                log.info("Sent KYC rejected event for user: {} with reason: {}", userId, event.getReason());
            }

            // Set process variables for next steps
            execution.setVariable("kycStatus", finalStatus.toString());
            execution.setVariable("kycLevel", kycLevel);
            execution.setVariable("kycDecisionMade", true);

            log.info("KYC status updated for user: {} to {} with level: {} and risk score: {}",
                    userId, finalStatus, kycLevel, riskScore);

        } catch (Exception e) {
            log.error("Error updating KYC status", e);
            throw e;
        }
    }

    private String determineRejectionReason(DelegateExecution execution) {
        StringBuilder reason = new StringBuilder();
        
        Boolean identityVerified = (Boolean) execution.getVariable("identityVerificationSuccess");
        if (identityVerified != null && !identityVerified) {
            reason.append("Identity verification failed. ");
        }
        
        Boolean documentVerified = (Boolean) execution.getVariable("documentVerificationSuccess");
        if (documentVerified != null && !documentVerified) {
            reason.append("Document verification failed. ");
        }
        
        Boolean hasSanctions = (Boolean) execution.getVariable("hasSanctionsMatch");
        if (hasSanctions != null && hasSanctions) {
            reason.append("Sanctions list match found. ");
        }
        
        Integer riskScore = (Integer) execution.getVariable("riskScore");
        if (riskScore != null && riskScore >= 70) {
            reason.append("High risk score: ").append(riskScore).append(". ");
        }
        
        String reviewNotes = (String) execution.getVariable("reviewNotes");
        if (reviewNotes != null && execution.getVariable("reviewDecision").equals("reject")) {
            reason.append("Manual review: ").append(reviewNotes);
        }
        
        return reason.length() > 0 ? reason.toString() : "Risk assessment failed";
    }
}