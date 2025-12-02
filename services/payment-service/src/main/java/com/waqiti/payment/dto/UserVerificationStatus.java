package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for user verification status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserVerificationStatus {
    
    private String userId;
    private boolean verified;
    private String status; // UNVERIFIED, PENDING, VERIFIED, REJECTED, EXPIRED
    
    // Individual verification components
    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean identityVerified;
    private boolean addressVerified;
    private boolean kycVerified;
    
    // Verification levels
    private String verificationLevel; // BASIC, STANDARD, ENHANCED
    private List<String> completedVerifications;
    private List<String> pendingVerifications;
    private List<String> requiredVerifications;
    
    // Verification history
    private Instant verifiedAt;
    private Instant expiresAt;
    private String verifiedBy;
    private String verificationMethod;
    
    // Document verification
    private boolean documentsSubmitted;
    private boolean documentsApproved;
    private List<String> submittedDocumentTypes;
    private List<String> approvedDocumentTypes;
    private List<String> rejectedDocumentTypes;
    
    // Additional checks
    private boolean sanctionsCheckPassed;
    private boolean pepCheckPassed;
    private boolean adverseMediaCheckPassed;
    
    // Failure reasons
    private List<String> rejectionReasons;
    private String lastRejectionReason;
    private Instant lastRejectionDate;
    
    // Next steps
    private List<String> nextSteps;
    private String nextStepDescription;
    
    private Map<String, Object> metadata;
}