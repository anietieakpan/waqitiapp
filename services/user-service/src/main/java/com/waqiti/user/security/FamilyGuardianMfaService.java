package com.waqiti.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditEvent;
import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.encryption.EncryptionService;
import com.waqiti.common.messaging.MessageService;
import com.waqiti.common.validation.ValidationService;
import com.waqiti.user.domain.User;
import com.waqiti.user.domain.FamilyGuardianship;
import com.waqiti.user.domain.GuardianApprovalRequest;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.FamilyGuardianshipRepository;
import com.waqiti.user.repository.GuardianApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Comprehensive family guardian approval service with 2FA
 * Manages multi-guardian approval workflows for family accounts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyGuardianMfaService {

    private static final String GUARDIAN_MFA_PREFIX = "guardian_mfa:";
    private static final String GUARDIAN_SESSION_PREFIX = "guardian_session:";
    private static final String APPROVAL_TRACKING_PREFIX = "approval_tracking:";
    private static final String FAILED_ATTEMPTS_PREFIX = "guardian_failed:";
    
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;
    private static final int OTP_LENGTH = 6;
    private static final int CHALLENGE_EXPIRY_MINUTES = 30;
    private static final int SESSION_EXPIRY_MINUTES = 120;
    private static final int APPROVAL_REQUEST_EXPIRY_HOURS = 24;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final FamilyGuardianshipRepository guardianshipRepository;
    private final GuardianApprovalRequestRepository approvalRequestRepository;
    private final MessageService messageService;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final ValidationService validationService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Determines guardian approval requirements for family account action
     */
    public GuardianApprovalRequirement determineGuardianApprovalRequirement(String dependentUserId, 
                                                                            GuardianActionContext context) {
        try {
            log.info("Determining guardian approval requirement for user {} action: {}", 
                dependentUserId, context.getActionType());
            
            User dependent = userRepository.findById(UUID.fromString(dependentUserId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Get active guardianships for this dependent
            List<FamilyGuardianship> guardianships = guardianshipRepository
                .findByDependentUserIdAndStatus(UUID.fromString(dependentUserId), 
                    FamilyGuardianship.GuardianshipStatus.ACTIVE);

            if (guardianships.isEmpty()) {
                return GuardianApprovalRequirement.builder()
                    .required(false)
                    .reason("No active guardians found for this account")
                    .build();
            }

            // Assess action risk and determine required guardians
            GuardianActionRiskLevel riskLevel = assessActionRisk(context, dependent);
            Set<UUID> requiredGuardianIds = determineRequiredGuardians(guardianships, riskLevel, context);
            
            if (requiredGuardianIds.isEmpty()) {
                return GuardianApprovalRequirement.builder()
                    .required(false)
                    .reason("Action does not require guardian approval")
                    .build();
            }

            return GuardianApprovalRequirement.builder()
                .required(true)
                .riskLevel(riskLevel)
                .requiredGuardianIds(requiredGuardianIds)
                .allGuardiansMustApprove(riskLevel == GuardianActionRiskLevel.CRITICAL)
                .approvalExpiryHours(APPROVAL_REQUEST_EXPIRY_HOURS)
                .reason(buildRequirementReason(riskLevel, context, requiredGuardianIds.size()))
                .build();

        } catch (Exception e) {
            log.error("Error determining guardian approval requirement for user {}: {}", 
                dependentUserId, e.getMessage(), e);
            auditGuardianFailure(dependentUserId, context, "REQUIREMENT_ERROR", e.getMessage());
            throw new RuntimeException("Failed to assess guardian approval requirements", e);
        }
    }

    /**
     * Initiates guardian approval request with 2FA challenges
     */
    @Transactional
    public GuardianApprovalChallenge initiateGuardianApproval(String dependentUserId, String actionId,
                                                             GuardianApprovalRequirement requirement) {
        try {
            log.info("Initiating guardian approval for user {} action {}", dependentUserId, actionId);
            
            String approvalRequestId = UUID.randomUUID().toString();
            
            // Create approval request record
            GuardianApprovalRequest approvalRequest = createApprovalRequest(
                approvalRequestId, dependentUserId, actionId, requirement);
            approvalRequestRepository.save(approvalRequest);
            
            // Generate MFA challenges for each required guardian
            Map<UUID, GuardianMfaChallenge> guardianChallenges = new HashMap<>();
            
            for (UUID guardianId : requirement.getRequiredGuardianIds()) {
                GuardianMfaChallenge challenge = generateGuardianMfaChallenge(
                    guardianId, approvalRequestId, requirement);
                guardianChallenges.put(guardianId, challenge);
                
                // Send notification to guardian
                sendGuardianNotification(guardianId, dependentUserId, challenge, approvalRequest);
            }
            
            // Store approval tracking data
            storeApprovalTracking(approvalRequestId, requirement, guardianChallenges);
            
            auditGuardianEvent(dependentUserId, actionId, "APPROVAL_INITIATED", 
                Map.of("approvalRequestId", approvalRequestId, 
                       "guardianCount", requirement.getRequiredGuardianIds().size()));
            
            return GuardianApprovalChallenge.builder()
                .approvalRequestId(approvalRequestId)
                .requiredGuardians(new ArrayList<>(requirement.getRequiredGuardianIds()))
                .allMustApprove(requirement.isAllGuardiansMustApprove())
                .expiresAt(LocalDateTime.now().plusHours(APPROVAL_REQUEST_EXPIRY_HOURS))
                .guardianChallenges(guardianChallenges)
                .message(buildApprovalMessage(requirement))
                .build();

        } catch (Exception e) {
            log.error("Error initiating guardian approval for user {}: {}", 
                dependentUserId, e.getMessage(), e);
            throw new RuntimeException("Failed to initiate guardian approval", e);
        }
    }

    /**
     * Processes guardian MFA response and updates approval status
     */
    @Transactional
    public GuardianMfaVerificationResult verifyGuardianMfa(String approvalRequestId, String guardianId,
                                                          String challengeId, Map<GuardianMfaMethod, String> responses) {
        try {
            log.info("Verifying guardian MFA for approval {} guardian {}", approvalRequestId, guardianId);
            
            UUID guardianUuid = UUID.fromString(guardianId);
            
            // Retrieve approval request
            GuardianApprovalRequest approvalRequest = approvalRequestRepository
                .findByApprovalRequestId(approvalRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));

            // Check if already expired
            if (approvalRequest.getExpiresAt().isBefore(LocalDateTime.now())) {
                return GuardianMfaVerificationResult.builder()
                    .success(false)
                    .errorMessage("Approval request has expired")
                    .build();
            }

            // Check if guardian is locked
            if (isGuardianLocked(guardianUuid, approvalRequestId)) {
                return GuardianMfaVerificationResult.builder()
                    .success(false)
                    .guardianLocked(true)
                    .errorMessage("Guardian temporarily locked due to failed attempts")
                    .build();
            }

            // Retrieve challenge data
            GuardianMfaChallengeData challengeData = retrieveGuardianChallenge(challengeId);
            if (challengeData == null || !challengeData.getGuardianId().equals(guardianUuid)) {
                return GuardianMfaVerificationResult.builder()
                    .success(false)
                    .errorMessage("Invalid or expired challenge")
                    .build();
            }

            // Verify MFA responses
            boolean verified = verifyGuardianMfaResponses(challengeData, responses);
            
            if (!verified) {
                incrementGuardianFailedAttempts(guardianUuid, approvalRequestId);
                
                auditGuardianEvent(approvalRequest.getDependentUserId().toString(), 
                    approvalRequest.getActionId(), "MFA_VERIFICATION_FAILED", 
                    Map.of("guardianId", guardianId, "approvalRequestId", approvalRequestId));
                
                return GuardianMfaVerificationResult.builder()
                    .success(false)
                    .errorMessage("MFA verification failed")
                    .attemptsRemaining(MAX_FAILED_ATTEMPTS - getFailedAttemptCount(guardianUuid, approvalRequestId))
                    .build();
            }

            // MFA verified - record guardian approval
            recordGuardianApproval(approvalRequest, guardianUuid);
            
            // Check if all required approvals are complete
            boolean allApproved = checkAllApprovalsComplete(approvalRequest);
            
            if (allApproved) {
                approvalRequest.setStatus(GuardianApprovalRequest.ApprovalStatus.APPROVED);
                approvalRequest.setCompletedAt(LocalDateTime.now());
                approvalRequestRepository.save(approvalRequest);
                
                // Create approval session for the dependent
                String approvalSession = createApprovalSession(
                    approvalRequest.getDependentUserId().toString(), approvalRequestId);
                
                auditGuardianEvent(approvalRequest.getDependentUserId().toString(), 
                    approvalRequest.getActionId(), "APPROVAL_COMPLETED", 
                    Map.of("approvalRequestId", approvalRequestId, "sessionToken", approvalSession));
                
                return GuardianMfaVerificationResult.builder()
                    .success(true)
                    .allApprovalsComplete(true)
                    .approvalSessionToken(approvalSession)
                    .sessionExpiresAt(LocalDateTime.now().plusMinutes(SESSION_EXPIRY_MINUTES))
                    .message("All guardian approvals complete - action authorized")
                    .build();
            } else {
                auditGuardianEvent(approvalRequest.getDependentUserId().toString(), 
                    approvalRequest.getActionId(), "GUARDIAN_APPROVED", 
                    Map.of("guardianId", guardianId, "approvalRequestId", approvalRequestId));
                
                return GuardianMfaVerificationResult.builder()
                    .success(true)
                    .allApprovalsComplete(false)
                    .message("Guardian approval recorded - waiting for additional approvals")
                    .pendingGuardians(getPendingGuardians(approvalRequest))
                    .build();
            }

        } catch (Exception e) {
            log.error("Error verifying guardian MFA for approval {}: {}", 
                approvalRequestId, e.getMessage(), e);
            throw new RuntimeException("Failed to verify guardian MFA", e);
        }
    }

    /**
     * Validates approval session for family account action
     */
    public boolean validateApprovalSession(String sessionToken, String dependentUserId, String actionType) {
        try {
            String sessionKey = GUARDIAN_SESSION_PREFIX + sessionToken;
            String sessionData = redisTemplate.opsForValue().get(sessionKey);
            
            if (sessionData == null) {
                return false;
            }

            Map<String, Object> session = objectMapper.readValue(sessionData, Map.class);
            
            return dependentUserId.equals(session.get("dependentUserId")) && 
                   actionType.equals(session.get("actionType")) &&
                   LocalDateTime.parse((String) session.get("expiresAt")).isAfter(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error validating approval session {}: {}", sessionToken, e.getMessage());
            return false;
        }
    }

    /**
     * Gets approval status for a request
     */
    public GuardianApprovalStatus getApprovalStatus(String approvalRequestId) {
        try {
            GuardianApprovalRequest request = approvalRequestRepository
                .findByApprovalRequestId(approvalRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));

            List<UUID> approvedGuardians = request.getApprovals().stream()
                .map(approval -> approval.getGuardianId())
                .collect(Collectors.toList());

            List<UUID> pendingGuardians = new ArrayList<>(request.getRequiredGuardianIds());
            pendingGuardians.removeAll(approvedGuardians);

            return GuardianApprovalStatus.builder()
                .approvalRequestId(approvalRequestId)
                .status(request.getStatus())
                .requiredGuardians(new ArrayList<>(request.getRequiredGuardianIds()))
                .approvedGuardians(approvedGuardians)
                .pendingGuardians(pendingGuardians)
                .allMustApprove(request.isAllGuardiansMustApprove())
                .expiresAt(request.getExpiresAt())
                .createdAt(request.getCreatedAt())
                .completedAt(request.getCompletedAt())
                .build();

        } catch (Exception e) {
            log.error("Error getting approval status for {}: {}", approvalRequestId, e.getMessage());
            throw new RuntimeException("Failed to get approval status", e);
        }
    }

    /**
     * Gets active guardianships for a dependent
     */
    public List<GuardianInfo> getActiveGuardians(String dependentUserId) {
        try {
            List<FamilyGuardianship> guardianships = guardianshipRepository
                .findByDependentUserIdAndStatus(UUID.fromString(dependentUserId), 
                    FamilyGuardianship.GuardianshipStatus.ACTIVE);

            return guardianships.stream()
                .map(guardianship -> {
                    User guardian = userRepository.findById(guardianship.getGuardianUserId())
                        .orElse(null);
                    
                    return GuardianInfo.builder()
                        .guardianId(guardianship.getGuardianUserId())
                        .guardianName(guardian != null ? guardian.getUsername() : "Unknown")
                        .guardianEmail(guardian != null ? guardian.getEmail() : null)
                        .guardianType(guardianship.getGuardianType())
                        .permissions(guardianship.getPermissions())
                        .establishedAt(guardianship.getCreatedAt())
                        .build();
                })
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting active guardians for user {}: {}", dependentUserId, e.getMessage());
            throw new RuntimeException("Failed to get active guardians", e);
        }
    }

    // Private helper methods

    private GuardianActionRiskLevel assessActionRisk(GuardianActionContext context, User dependent) {
        int riskScore = 0;
        
        // Action type risk scoring
        switch (context.getActionType()) {
            case DELETE_ACCOUNT:
            case TRANSFER_OWNERSHIP:
            case CHANGE_GUARDIAN:
                riskScore += 50;
                break;
            case LARGE_TRANSACTION:
            case CHANGE_LIMITS:
                riskScore += 30;
                break;
            case CHANGE_PROFILE:
            case CHANGE_SETTINGS:
                riskScore += 15;
                break;
            case VIEW_STATEMENTS:
            case MINOR_SETTINGS:
                riskScore += 5;
                break;
        }

        // Amount-based risk (for financial actions)
        if (context.getAmount() != null) {
            if (context.getAmount().compareTo(new java.math.BigDecimal("10000")) > 0) {
                riskScore += 25;
            } else if (context.getAmount().compareTo(new java.math.BigDecimal("1000")) > 0) {
                riskScore += 10;
            }
        }

        // Time-based risk
        if (isOutOfHours(context.getTimestamp())) {
            riskScore += 10;
        }

        // Device and location risk
        if (!context.getDeviceInfo().isTrusted()) {
            riskScore += 15;
        }

        if (!context.getLocationInfo().isTrusted()) {
            riskScore += 10;
        }

        // Age-based considerations for minor accounts
        if (dependent.getDateOfBirth() != null) {
            int age = java.time.Period.between(dependent.getDateOfBirth(), 
                java.time.LocalDate.now()).getYears();
            if (age < 16) {
                riskScore += 20;  // Higher protection for younger users
            } else if (age < 18) {
                riskScore += 10;
            }
        }

        // Classify risk level
        if (riskScore >= 70) {
            return GuardianActionRiskLevel.CRITICAL;
        } else if (riskScore >= 50) {
            return GuardianActionRiskLevel.HIGH;
        } else if (riskScore >= 25) {
            return GuardianActionRiskLevel.MEDIUM;
        } else {
            return GuardianActionRiskLevel.LOW;
        }
    }

    private Set<UUID> determineRequiredGuardians(List<FamilyGuardianship> guardianships, 
                                                GuardianActionRiskLevel riskLevel, 
                                                GuardianActionContext context) {
        Set<UUID> requiredGuardians = new HashSet<>();
        
        // Filter guardians by permissions for the action
        List<FamilyGuardianship> eligibleGuardians = guardianships.stream()
            .filter(g -> hasRequiredPermission(g, context.getActionType()))
            .collect(Collectors.toList());

        if (eligibleGuardians.isEmpty()) {
            return requiredGuardians;  // No eligible guardians
        }

        switch (riskLevel) {
            case CRITICAL:
                // All eligible guardians must approve
                requiredGuardians.addAll(eligibleGuardians.stream()
                    .map(FamilyGuardianship::getGuardianUserId)
                    .collect(Collectors.toSet()));
                break;
            case HIGH:
                // At least 2 guardians or all if less than 2
                if (eligibleGuardians.size() >= 2) {
                    // Prioritize primary guardians
                    eligibleGuardians.stream()
                        .filter(g -> g.getGuardianType() == FamilyGuardianship.GuardianType.PRIMARY)
                        .limit(2)
                        .forEach(g -> requiredGuardians.add(g.getGuardianUserId()));
                    
                    // If we don't have 2 primary guardians, add secondary ones
                    if (requiredGuardians.size() < 2) {
                        eligibleGuardians.stream()
                            .filter(g -> !requiredGuardians.contains(g.getGuardianUserId()))
                            .limit(2 - requiredGuardians.size())
                            .forEach(g -> requiredGuardians.add(g.getGuardianUserId()));
                    }
                } else {
                    requiredGuardians.addAll(eligibleGuardians.stream()
                        .map(FamilyGuardianship::getGuardianUserId)
                        .collect(Collectors.toSet()));
                }
                break;
            case MEDIUM:
            case LOW:
                // At least 1 guardian, prioritize primary
                Optional<FamilyGuardianship> primaryGuardian = eligibleGuardians.stream()
                    .filter(g -> g.getGuardianType() == FamilyGuardianship.GuardianType.PRIMARY)
                    .findFirst();
                
                if (primaryGuardian.isPresent()) {
                    requiredGuardians.add(primaryGuardian.get().getGuardianUserId());
                } else {
                    requiredGuardians.add(eligibleGuardians.get(0).getGuardianUserId());
                }
                break;
        }

        return requiredGuardians;
    }

    private boolean hasRequiredPermission(FamilyGuardianship guardianship, GuardianActionType actionType) {
        Set<String> permissions = guardianship.getPermissions();
        
        switch (actionType) {
            case DELETE_ACCOUNT:
            case TRANSFER_OWNERSHIP:
                return permissions.contains("ACCOUNT_MANAGEMENT");
            case CHANGE_GUARDIAN:
                return permissions.contains("GUARDIAN_MANAGEMENT");
            case LARGE_TRANSACTION:
                return permissions.contains("FINANCIAL_OVERSIGHT");
            case CHANGE_LIMITS:
                return permissions.contains("LIMIT_MANAGEMENT");
            case CHANGE_PROFILE:
            case CHANGE_SETTINGS:
                return permissions.contains("PROFILE_MANAGEMENT");
            case VIEW_STATEMENTS:
                return permissions.contains("VIEW_FINANCIAL");
            case MINOR_SETTINGS:
            default:
                return permissions.contains("BASIC_OVERSIGHT");
        }
    }

    private GuardianMfaChallenge generateGuardianMfaChallenge(UUID guardianId, String approvalRequestId,
                                                             GuardianApprovalRequirement requirement) {
        User guardian = userRepository.findById(guardianId)
            .orElseThrow(() -> new IllegalArgumentException("Guardian not found"));

        String challengeId = UUID.randomUUID().toString();
        
        // Generate OTP challenges
        Map<GuardianMfaMethod, String> challenges = new HashMap<>();
        
        if (guardian.getPhoneVerified()) {
            challenges.put(GuardianMfaMethod.SMS_OTP, generateOtp());
        }
        
        challenges.put(GuardianMfaMethod.EMAIL_OTP, generateOtp());
        
        // For high-risk actions, add additional verification
        if (requirement.getRiskLevel().ordinal() >= GuardianActionRiskLevel.HIGH.ordinal()) {
            challenges.put(GuardianMfaMethod.SECURITY_QUESTIONS, "Answer your security questions");
        }

        // Store challenge data
        GuardianMfaChallengeData challengeData = GuardianMfaChallengeData.builder()
            .challengeId(challengeId)
            .guardianId(guardianId)
            .approvalRequestId(approvalRequestId)
            .requiredMethods(challenges.keySet())
            .challenges(challenges)
            .attempts(0)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(CHALLENGE_EXPIRY_MINUTES))
            .build();
        
        storeGuardianChallenge(challengeId, challengeData);
        
        return GuardianMfaChallenge.builder()
            .challengeId(challengeId)
            .guardianId(guardianId)
            .requiredMethods(new ArrayList<>(challenges.keySet()))
            .expiresAt(challengeData.getExpiresAt())
            .message("Guardian approval required - complete MFA verification")
            .build();
    }

    private void sendGuardianNotification(UUID guardianId, String dependentUserId, 
                                        GuardianMfaChallenge challenge, 
                                        GuardianApprovalRequest approvalRequest) {
        try {
            User guardian = userRepository.findById(guardianId).orElse(null);
            User dependent = userRepository.findById(UUID.fromString(dependentUserId)).orElse(null);
            
            if (guardian == null || dependent == null) {
                return;
            }

            String subject = "Guardian Approval Required - " + dependent.getUsername();
            String message = String.format(
                "Guardian approval is required for account action.\n\n" +
                "Dependent: %s\n" +
                "Action: %s\n" +
                "Risk Level: %s\n" +
                "Approval ID: %s\n" +
                "Expires: %s\n\n" +
                "Please complete MFA verification to approve or deny this request.",
                dependent.getUsername(),
                approvalRequest.getActionType(),
                approvalRequest.getRiskLevel(),
                approvalRequest.getApprovalRequestId(),
                approvalRequest.getExpiresAt()
            );

            // Send email notification
            messageService.sendEmail(guardian.getEmail(), subject, message);

            // Send SMS if available and action is high risk
            if (guardian.getPhoneVerified() && 
                approvalRequest.getRiskLevel().ordinal() >= GuardianActionRiskLevel.HIGH.ordinal()) {
                
                String smsCode = challenge.getRequiredMethods().contains(GuardianMfaMethod.SMS_OTP) ? 
                    " SMS code: " + getGuardianChallengeResponse(challenge.getChallengeId(), GuardianMfaMethod.SMS_OTP) : "";
                
                messageService.sendSms(guardian.getPhoneNumber(),
                    String.format("Guardian approval needed for %s. Approval ID: %s%s", 
                        dependent.getUsername(), approvalRequest.getApprovalRequestId(), smsCode));
            }

        } catch (Exception e) {
            log.error("Failed to send guardian notification to {}: {}", guardianId, e.getMessage());
        }
    }

    private boolean verifyGuardianMfaResponses(GuardianMfaChallengeData challengeData, 
                                             Map<GuardianMfaMethod, String> responses) {
        for (GuardianMfaMethod method : challengeData.getRequiredMethods()) {
            String response = responses.get(method);
            String expectedResponse = challengeData.getChallenges().get(method);
            
            if (response == null) {
                return false;
            }

            switch (method) {
                case SMS_OTP:
                case EMAIL_OTP:
                    if (!response.equals(expectedResponse)) {
                        return false;
                    }
                    break;
                case SECURITY_QUESTIONS:
                    if (!validationService.validateSecurityQuestions(
                            challengeData.getGuardianId().toString(), response)) {
                        return false;
                    }
                    break;
            }
        }
        
        return true;
    }

    private void recordGuardianApproval(GuardianApprovalRequest request, UUID guardianId) {
        request.addApproval(guardianId, LocalDateTime.now());
        approvalRequestRepository.save(request);
        
        resetGuardianFailedAttempts(guardianId, request.getApprovalRequestId());
        log.info("Guardian {} approval recorded for request {}", guardianId, request.getApprovalRequestId());
    }

    private boolean checkAllApprovalsComplete(GuardianApprovalRequest request) {
        if (request.isAllGuardiansMustApprove()) {
            return request.getApprovals().size() == request.getRequiredGuardianIds().size();
        } else {
            // At least one approval is sufficient for non-critical actions
            return request.getApprovals().size() > 0;
        }
    }

    private String createApprovalSession(String dependentUserId, String approvalRequestId) {
        String sessionToken = UUID.randomUUID().toString();
        String sessionKey = GUARDIAN_SESSION_PREFIX + sessionToken;
        
        Map<String, Object> sessionData = Map.of(
            "dependentUserId", dependentUserId,
            "approvalRequestId", approvalRequestId,
            "createdAt", LocalDateTime.now().toString(),
            "expiresAt", LocalDateTime.now().plusMinutes(SESSION_EXPIRY_MINUTES).toString()
        );
        
        try {
            String sessionJson = objectMapper.writeValueAsString(sessionData);
            redisTemplate.opsForValue().set(sessionKey, sessionJson, SESSION_EXPIRY_MINUTES, TimeUnit.MINUTES);
            return sessionToken;
        } catch (Exception e) {
            log.error("Failed to create approval session: {}", e.getMessage());
            throw new RuntimeException("Failed to create approval session", e);
        }
    }

    private List<UUID> getPendingGuardians(GuardianApprovalRequest request) {
        Set<UUID> approved = request.getApprovals().stream()
            .map(approval -> approval.getGuardianId())
            .collect(Collectors.toSet());
        
        return request.getRequiredGuardianIds().stream()
            .filter(id -> !approved.contains(id))
            .collect(Collectors.toList());
    }

    // Helper methods for Redis operations, OTP generation, etc.
    
    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }

    private boolean isOutOfHours(LocalDateTime timestamp) {
        int hour = timestamp.getHour();
        return hour < 6 || hour > 22;
    }

    private GuardianApprovalRequest createApprovalRequest(String approvalRequestId, String dependentUserId,
                                                        String actionId, GuardianApprovalRequirement requirement) {
        return GuardianApprovalRequest.builder()
            .approvalRequestId(approvalRequestId)
            .dependentUserId(UUID.fromString(dependentUserId))
            .actionId(actionId)
            .actionType(requirement.getActionType())
            .riskLevel(requirement.getRiskLevel())
            .requiredGuardianIds(requirement.getRequiredGuardianIds())
            .allGuardiansMustApprove(requirement.isAllGuardiansMustApprove())
            .status(GuardianApprovalRequest.ApprovalStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(requirement.getApprovalExpiryHours()))
            .build();
    }

    private void storeGuardianChallenge(String challengeId, GuardianMfaChallengeData challengeData) {
        try {
            String challengeKey = GUARDIAN_MFA_PREFIX + challengeId;
            String challengeJson = objectMapper.writeValueAsString(challengeData);
            redisTemplate.opsForValue().set(challengeKey, challengeJson, 
                CHALLENGE_EXPIRY_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Failed to store guardian challenge {}: {}", challengeId, e.getMessage());
            throw new RuntimeException("Failed to store challenge", e);
        }
    }

    private GuardianMfaChallengeData retrieveGuardianChallenge(String challengeId) {
        try {
            String challengeKey = GUARDIAN_MFA_PREFIX + challengeId;
            String challengeJson = redisTemplate.opsForValue().get(challengeKey);
            
            if (challengeJson == null) {
                return null;
            }
            
            return objectMapper.readValue(challengeJson, GuardianMfaChallengeData.class);
        } catch (Exception e) {
            log.error("Failed to retrieve guardian challenge {}: {}", challengeId, e.getMessage());
            return null;
        }
    }

    private void storeApprovalTracking(String approvalRequestId, GuardianApprovalRequirement requirement,
                                     Map<UUID, GuardianMfaChallenge> guardianChallenges) {
        try {
            String trackingKey = APPROVAL_TRACKING_PREFIX + approvalRequestId;
            Map<String, Object> trackingData = Map.of(
                "approvalRequestId", approvalRequestId,
                "requiredGuardians", requirement.getRequiredGuardianIds(),
                "challenges", guardianChallenges.keySet(),
                "createdAt", LocalDateTime.now().toString()
            );
            
            String trackingJson = objectMapper.writeValueAsString(trackingData);
            redisTemplate.opsForValue().set(trackingKey, trackingJson, 
                APPROVAL_REQUEST_EXPIRY_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to store approval tracking for {}: {}", approvalRequestId, e.getMessage());
        }
    }

    private boolean isGuardianLocked(UUID guardianId, String approvalRequestId) {
        String lockKey = FAILED_ATTEMPTS_PREFIX + guardianId + ":" + approvalRequestId + ":locked";
        return redisTemplate.hasKey(lockKey);
    }

    private void incrementGuardianFailedAttempts(UUID guardianId, String approvalRequestId) {
        String attemptsKey = FAILED_ATTEMPTS_PREFIX + guardianId + ":" + approvalRequestId + ":count";
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        
        Long attempts = ops.increment(attemptsKey);
        redisTemplate.expire(attemptsKey, LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
        
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            String lockKey = FAILED_ATTEMPTS_PREFIX + guardianId + ":" + approvalRequestId + ":locked";
            redisTemplate.opsForValue().set(lockKey, "locked", LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
            
            log.warn("Guardian {} locked for approval {} after {} failed attempts", 
                guardianId, approvalRequestId, attempts);
        }
    }

    private void resetGuardianFailedAttempts(UUID guardianId, String approvalRequestId) {
        String attemptsKey = FAILED_ATTEMPTS_PREFIX + guardianId + ":" + approvalRequestId + ":count";
        String lockKey = FAILED_ATTEMPTS_PREFIX + guardianId + ":" + approvalRequestId + ":locked";
        
        redisTemplate.delete(attemptsKey);
        redisTemplate.delete(lockKey);
    }

    private int getFailedAttemptCount(UUID guardianId, String approvalRequestId) {
        String attemptsKey = FAILED_ATTEMPTS_PREFIX + guardianId + ":" + approvalRequestId + ":count";
        String attempts = redisTemplate.opsForValue().get(attemptsKey);
        return attempts != null ? Integer.parseInt(attempts) : 0;
    }

    private String getGuardianChallengeResponse(String challengeId, GuardianMfaMethod method) {
        GuardianMfaChallengeData challengeData = retrieveGuardianChallenge(challengeId);
        return challengeData != null ? challengeData.getChallenges().get(method) : null;
    }

    private String buildRequirementReason(GuardianActionRiskLevel riskLevel, GuardianActionContext context, int guardianCount) {
        return String.format("Action '%s' requires %s risk approval from %d guardian(s)", 
            context.getActionType(), riskLevel.toString().toLowerCase(), guardianCount);
    }

    private String buildApprovalMessage(GuardianApprovalRequirement requirement) {
        String approvalType = requirement.isAllGuardiansMustApprove() ? "all" : "at least one";
        return String.format("Guardian approval required - %s of %d guardian(s) must approve", 
            approvalType, requirement.getRequiredGuardianIds().size());
    }

    private void auditGuardianEvent(String dependentUserId, String actionId, String eventType, 
                                   Map<String, Object> details) {
        try {
            AuditEvent event = AuditEvent.builder()
                .eventType(AuditEventType.AUTHENTICATION)
                .userId(dependentUserId)
                .resourceId(actionId)
                .resourceType("FAMILY_GUARDIAN_APPROVAL")
                .action("GUARDIAN_" + eventType)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
            
            auditService.logEvent(event);
        } catch (Exception e) {
            log.error("Failed to audit guardian event for user {}: {}", dependentUserId, e.getMessage());
        }
    }

    private void auditGuardianFailure(String dependentUserId, GuardianActionContext context, 
                                    String errorType, String errorMessage) {
        auditGuardianEvent(dependentUserId, context != null ? context.getActionId() : null, 
            "FAILURE", Map.of("errorType", errorType, "errorMessage", errorMessage));
    }

    // Enums and Data Classes

    public enum GuardianActionRiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum GuardianActionType {
        DELETE_ACCOUNT, TRANSFER_OWNERSHIP, CHANGE_GUARDIAN, LARGE_TRANSACTION,
        CHANGE_LIMITS, CHANGE_PROFILE, CHANGE_SETTINGS, VIEW_STATEMENTS, MINOR_SETTINGS
    }

    public enum GuardianMfaMethod {
        SMS_OTP, EMAIL_OTP, SECURITY_QUESTIONS
    }

    @lombok.Data
    @lombok.Builder
    public static class GuardianActionContext {
        private String actionId;
        private GuardianActionType actionType;
        private LocalDateTime timestamp;
        private java.math.BigDecimal amount; // For financial actions
        private DeviceInfo deviceInfo;
        private LocationInfo locationInfo;
        private Map<String, Object> actionDetails;
    }

    @lombok.Data
    @lombok.Builder
    public static class DeviceInfo {
        private String deviceId;
        private String deviceType;
        private boolean trusted;
        private LocalDateTime lastSeen;
    }

    @lombok.Data
    @lombok.Builder
    public static class LocationInfo {
        private String ipAddress;
        private String countryCode;
        private String city;
        private boolean trusted;
        private boolean vpnDetected;
    }

    @lombok.Data
    @lombok.Builder
    public static class GuardianApprovalRequirement {
        private boolean required;
        private GuardianActionRiskLevel riskLevel;
        private GuardianActionType actionType;
        private Set<UUID> requiredGuardianIds;
        private boolean allGuardiansMustApprove;
        private int approvalExpiryHours;
        private String reason;
    }

    @lombok.Data
    @lombok.Builder
    public static class GuardianApprovalChallenge {
        private String approvalRequestId;
        private List<UUID> requiredGuardians;
        private boolean allMustApprove;
        private LocalDateTime expiresAt;
        private Map<UUID, GuardianMfaChallenge> guardianChallenges;
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    public static class GuardianMfaChallenge {
        private String challengeId;
        private UUID guardianId;
        private List<GuardianMfaMethod> requiredMethods;
        private LocalDateTime expiresAt;
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    public static class GuardianMfaVerificationResult {
        private boolean success;
        private boolean guardianLocked;
        private boolean allApprovalsComplete;
        private String approvalSessionToken;
        private LocalDateTime sessionExpiresAt;
        private String message;
        private String errorMessage;
        private List<UUID> pendingGuardians;
        private int attemptsRemaining;
    }

    @lombok.Data
    @lombok.Builder
    public static class GuardianApprovalStatus {
        private String approvalRequestId;
        private GuardianApprovalRequest.ApprovalStatus status;
        private List<UUID> requiredGuardians;
        private List<UUID> approvedGuardians;
        private List<UUID> pendingGuardians;
        private boolean allMustApprove;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class GuardianInfo {
        private UUID guardianId;
        private String guardianName;
        private String guardianEmail;
        private FamilyGuardianship.GuardianType guardianType;
        private Set<String> permissions;
        private LocalDateTime establishedAt;
    }

    @lombok.Data
    @lombok.Builder
    private static class GuardianMfaChallengeData {
        private String challengeId;
        private UUID guardianId;
        private String approvalRequestId;
        private Set<GuardianMfaMethod> requiredMethods;
        private Map<GuardianMfaMethod, String> challenges;
        private int attempts;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }
}