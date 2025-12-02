package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Event for KYC rejection
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserKycRejectedEvent extends UserEvent {
    
    private String email;
    private String kycRequestId;
    private String rejectionReason;
    private List<String> failedChecks;
    private String documentType;
    private String documentNumber;
    private String verificationMethod; // MANUAL, AUTOMATED, HYBRID
    private String reviewedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime rejectedAt;
    private int attemptNumber;
    private int maxAttempts;
    private boolean canRetry;
    private LocalDateTime retryAfter;
    private String riskScore;
    private List<String> requiredDocuments;
    private Map<String, String> rejectionDetails;
    private String complianceNotes;
    private String nextAction; // RESUBMIT_DOCUMENTS, CONTACT_SUPPORT, PERMANENTLY_REJECTED
    
    public UserKycRejectedEvent() {
        super("USER_KYC_REJECTED");
    }
    
    public static UserKycRejectedEvent documentIssue(String userId, String email, String kycRequestId, 
                                                   String reason, List<String> failedChecks, 
                                                   boolean canRetry) {
        UserKycRejectedEvent event = new UserKycRejectedEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setKycRequestId(kycRequestId);
        event.setRejectionReason(reason);
        event.setFailedChecks(failedChecks);
        event.setCanRetry(canRetry);
        event.setVerificationMethod("AUTOMATED");
        event.setRejectedAt(LocalDateTime.now());
        event.setNextAction(canRetry ? "RESUBMIT_DOCUMENTS" : "CONTACT_SUPPORT");
        return event;
    }
    
    public static UserKycRejectedEvent complianceIssue(String userId, String email, String kycRequestId, 
                                                     String reason, String riskScore, String reviewedBy) {
        UserKycRejectedEvent event = new UserKycRejectedEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setKycRequestId(kycRequestId);
        event.setRejectionReason(reason);
        event.setRiskScore(riskScore);
        event.setReviewedBy(reviewedBy);
        event.setVerificationMethod("MANUAL");
        event.setCanRetry(false);
        event.setRejectedAt(LocalDateTime.now());
        event.setNextAction("CONTACT_SUPPORT");
        return event;
    }
    
    public static UserKycRejectedEvent maxAttemptsReached(String userId, String email, String kycRequestId, 
                                                        int attemptNumber, int maxAttempts) {
        UserKycRejectedEvent event = new UserKycRejectedEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setKycRequestId(kycRequestId);
        event.setRejectionReason("Maximum KYC attempts reached");
        event.setAttemptNumber(attemptNumber);
        event.setMaxAttempts(maxAttempts);
        event.setCanRetry(false);
        event.setRejectedAt(LocalDateTime.now());
        event.setNextAction("PERMANENTLY_REJECTED");
        return event;
    }
}