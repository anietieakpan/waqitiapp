/**
 * BNPL Application Service
 * Core business logic for Buy Now Pay Later applications
 */
package com.waqiti.bnpl.service;

import com.waqiti.bnpl.dto.request.BnplApplicationRequest;
import com.waqiti.bnpl.dto.response.BnplApplicationResponse;
import com.waqiti.bnpl.entity.BnplApplication;
import com.waqiti.bnpl.entity.BnplApplication.ApplicationStatus;
import com.waqiti.bnpl.entity.BnplInstallment;
import com.waqiti.bnpl.entity.CreditAssessment;
import com.waqiti.bnpl.exception.BnplApplicationException;
import com.waqiti.bnpl.exception.CreditLimitExceededException;
import com.waqiti.bnpl.exception.DuplicateApplicationException;
import com.waqiti.bnpl.repository.BnplApplicationRepository;
import com.waqiti.bnpl.repository.BnplInstallmentRepository;
import com.waqiti.common.event.EventPublisher;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import com.waqiti.security.service.BnplFraudDetectionService;
import com.waqiti.security.dto.BnplFraudAnalysisRequest;
import com.waqiti.security.dto.BnplFraudAnalysisResponse;
import com.waqiti.common.lock.DistributedLockService;
import com.waqiti.common.lock.DistributedLockService.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BnplApplicationService {
    
    private final BnplApplicationRepository applicationRepository;
    private final BnplInstallmentRepository installmentRepository;
    private final CreditScoringService creditScoringService;
    private final BnplFraudDetectionService bnplFraudDetectionService;
    private final EventPublisher eventPublisher;
    private final KYCClientService kycClientService;
    private final DistributedLockService distributedLockService;
    
    private static final BigDecimal MIN_PURCHASE_AMOUNT = new BigDecimal("50.00");
    private static final BigDecimal MAX_PURCHASE_AMOUNT = new BigDecimal("10000.00");
    private static final int MAX_ACTIVE_APPLICATIONS = 3;
    
    /**
     * Creates and processes a new BNPL application
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "BNPL_APPLICATION")
    public BnplApplicationResponse createApplication(BnplApplicationRequest request) {
        log.info("Creating BNPL application for user {} amount {}", request.getUserId(), request.getPurchaseAmount());

        // Declare distributed lock at method level for proper cleanup
        DistributedLock creditLock = null;

        try {
            // 1. Validate request
            validateApplicationRequest(request);
            
            // 1.5. Enhanced KYC check for high-value BNPL applications
            if (request.getPurchaseAmount().compareTo(new BigDecimal("2000")) > 0) {
                if (!kycClientService.canUserMakeHighValueTransfer(request.getUserId().toString())) {
                    throw new BnplApplicationException("Enhanced KYC verification required for BNPL applications over $2,000");
                }
            }
            
            // 2. Check for duplicate
            if (applicationRepository.existsByUserIdAndOrderId(request.getUserId(), request.getOrderId())) {
                throw new DuplicateApplicationException("Application already exists for order: " + request.getOrderId());
            }
            
            // 3. Check active application limits
            Integer activeCount = applicationRepository.getActiveApplicationCount(request.getUserId());
            if (activeCount >= MAX_ACTIVE_APPLICATIONS) {
                throw new BnplApplicationException("Maximum active applications limit reached");
            }
            
            // 4. Perform credit assessment
            CreditAssessment assessment = creditScoringService.performCreditAssessment(
                    request.getUserId(),
                    null,
                    CreditAssessment.AssessmentType.INITIAL
            );

            // 5. DISTRIBUTED LOCK: Prevent race conditions in multi-instance deployments
            // Use Redis distributed lock to ensure only ONE process checks credit at a time
            // This prevents scenarios where 2+ simultaneous requests exceed credit limit
            String lockKey = "bnpl:credit-check:" + request.getUserId();
            DistributedLock creditLock = null;

            try {
                // Acquire distributed lock with 30 second TTL (auto-expires if process crashes)
                creditLock = distributedLockService.acquireLock(lockKey, Duration.ofSeconds(30));

                if (creditLock == null) {
                    log.warn("Failed to acquire credit check lock for user {}, concurrent application in progress",
                             request.getUserId());
                    throw new BnplApplicationException(
                        "Another credit application is currently being processed. Please try again in a few moments.");
                }

                log.debug("Distributed lock acquired for credit check: user={}, lock={}",
                         request.getUserId(), creditLock.getLockToken());

                // Now that we have the lock, check credit limit atomically
                BigDecimal totalExposure = applicationRepository.getTotalActiveFinancedAmount(request.getUserId());
                if (totalExposure == null) totalExposure = BigDecimal.ZERO;

                BigDecimal requestedAmount = request.getPurchaseAmount().subtract(request.getDownPayment());
                BigDecimal newTotalExposure = totalExposure.add(requestedAmount);

                log.info("Credit limit check for user {}: current={}, requested={}, new total={}, limit={}",
                        request.getUserId(), totalExposure, requestedAmount, newTotalExposure,
                        assessment.getRecommendedLimit());

                if (newTotalExposure.compareTo(assessment.getRecommendedLimit()) > 0) {
                    log.warn("Credit limit exceeded for user {}: attempted={}, limit={}",
                            request.getUserId(), newTotalExposure, assessment.getRecommendedLimit());
                    throw new CreditLimitExceededException(String.format(
                            "Credit limit exceeded. Available: %s, Requested: %s",
                            assessment.getRecommendedLimit().subtract(totalExposure),
                            requestedAmount));
                }

                // Credit check passed, continue with application creation
                // (Lock will be held until application is saved or exception occurs)

            } catch (CreditLimitExceededException e) {
                // Release lock immediately on credit limit failure
                if (creditLock != null) {
                    distributedLockService.releaseLock(creditLock);
                    creditLock = null; // Prevent double-release in finally block
                }
                throw e; // Re-throw to caller
            } catch (BnplApplicationException e) {
                // Release lock on application failure
                if (creditLock != null) {
                    distributedLockService.releaseLock(creditLock);
                    creditLock = null;
                }
                throw e;
            }
            
            // 6. Run comprehensive BNPL fraud detection
            BnplFraudAnalysisRequest fraudRequest = BnplFraudAnalysisRequest.builder()
                    .applicationId(UUID.randomUUID()) // Will be set after application creation
                    .userId(request.getUserId())
                    .merchantId(request.getMerchantId())
                    .merchantName(request.getMerchantName())
                    .orderId(request.getOrderId())
                    .purchaseAmount(request.getPurchaseAmount())
                    .downPayment(request.getDownPayment())
                    .financedAmount(requestedAmount)
                    .currency(request.getCurrency())
                    .requestedInstallments(request.getRequestedInstallments())
                    .creditScore(assessment.getCreditScore())
                    .thinCreditFile(assessment.getRiskTier() == CreditAssessment.RiskTier.HIGH)
                    .ipAddress(request.getIpAddress())
                    .deviceFingerprint(request.getDeviceFingerprint())
                    .userAgent(request.getUserAgent())
                    .applicationSource(request.getApplicationSource())
                    .isFirstTimeUser(applicationRepository.countByUserId(request.getUserId()) == 0)
                    .totalBnplExposure(totalExposure)
                    .build();
            
            BnplFraudAnalysisResponse fraudResult = bnplFraudDetectionService.analyzeBnplApplication(fraudRequest);
            
            // Block if high fraud risk
            if ("BLOCK_APPLICATION".equals(fraudResult.getRecommendedAction())) {
                return createRejectedApplication(request, "Failed fraud check: High risk score " + fraudResult.getFinalRiskScore());
            }
            
            // Require manual review for medium-high risk
            boolean requiresManualReview = "MANUAL_REVIEW_REQUIRED".equals(fraudResult.getRecommendedAction());
            
            // 7. Calculate terms
            ApplicationTerms terms = calculateApplicationTerms(request, assessment);
            
            // 8. Create application
            BnplApplication application = BnplApplication.builder()
                    .userId(request.getUserId())
                    .merchantId(request.getMerchantId())
                    .merchantName(request.getMerchantName())
                    .orderId(request.getOrderId())
                    .applicationNumber(generateApplicationNumber())
                    .status(ApplicationStatus.PENDING)
                    .purchaseAmount(request.getPurchaseAmount())
                    .currency(request.getCurrency())
                    .downPayment(request.getDownPayment())
                    .financedAmount(terms.financedAmount)
                    .installmentCount(terms.installmentCount)
                    .installmentAmount(terms.installmentAmount)
                    .interestRate(terms.interestRate)
                    .totalAmount(terms.totalAmount)
                    .firstPaymentDate(terms.firstPaymentDate)
                    .finalPaymentDate(terms.finalPaymentDate)
                    .creditScore(assessment.getCreditScore())
                    .riskTier(assessment.getRiskTier().name())
                    .riskFactors(assessment.getRiskFactors())
                    .applicationSource(request.getApplicationSource())
                    .deviceFingerprint(request.getDeviceFingerprint())
                    .ipAddress(request.getIpAddress())
                    .userAgent(request.getUserAgent())
                    .build();
            
            // 9. Auto-approve if low risk and fraud check passes
            if (assessment.getRiskTier() == CreditAssessment.RiskTier.LOW && 
                requestedAmount.compareTo(new BigDecimal("1000")) <= 0 &&
                !requiresManualReview &&
                fraudResult.getFinalRiskScore() < 50) {
                application.setStatus(ApplicationStatus.APPROVED);
                application.setDecision("APPROVED");
                application.setDecisionReason("Auto-approved: Low risk profile and fraud score");
                application.setDecisionDate(LocalDateTime.now());
                application.setDecisionBy("SYSTEM");
                application.setApprovalDate(LocalDateTime.now());
            } else if (requiresManualReview) {
                application.setDecisionReason("Manual review required due to fraud risk: " + fraudResult.getFinalRiskScore());
            }
            
            application = applicationRepository.save(application);

            // CRITICAL: Release distributed lock immediately after application is saved
            // This frees the lock for other concurrent requests
            if (creditLock != null) {
                distributedLockService.releaseLock(creditLock);
                log.debug("Distributed lock released after application creation: user={}", request.getUserId());
                creditLock = null;
            }

            // 10. Create installment schedule if approved
            if (application.isApproved()) {
                createInstallmentSchedule(application, terms);
            }

            // 11. Publish event
            publishApplicationEvent(application, "APPLICATION_CREATED");

            return mapToResponse(application);

        } catch (Exception e) {
            log.error("Failed to create BNPL application", e);

            // Ensure lock is released on any unexpected exception
            if (creditLock != null) {
                try {
                    distributedLockService.releaseLock(creditLock);
                    log.debug("Distributed lock released on exception: user={}", request.getUserId());
                } catch (Exception lockEx) {
                    log.error("Failed to release distributed lock on exception", lockEx);
                }
            }

            throw e;
        }
    }
    
    /**
     * Approves a pending application
     */
    @Transactional
    public BnplApplicationResponse approveApplication(UUID applicationId, String approvedBy) {
        log.info("Approving application {} by {}", applicationId, approvedBy);
        
        BnplApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BnplApplicationException("Application not found"));
        
        if (!application.isPending()) {
            throw new BnplApplicationException("Application is not in pending status");
        }
        
        application.setStatus(ApplicationStatus.APPROVED);
        application.setDecision("APPROVED");
        application.setDecisionDate(LocalDateTime.now());
        application.setDecisionBy(approvedBy);
        application.setApprovalDate(LocalDateTime.now());
        
        // Calculate terms if not already set
        if (application.getFirstPaymentDate() == null) {
            ApplicationTerms terms = calculateApplicationTerms(application);
            application.setFirstPaymentDate(terms.firstPaymentDate);
            application.setFinalPaymentDate(terms.finalPaymentDate);
        }
        
        application = applicationRepository.save(application);
        
        // Create installment schedule
        createInstallmentSchedule(application, null);
        
        publishApplicationEvent(application, "APPLICATION_APPROVED");
        
        return mapToResponse(application);
    }
    
    /**
     * Rejects a pending application
     */
    @Transactional
    public BnplApplicationResponse rejectApplication(UUID applicationId, String reason, String rejectedBy) {
        log.info("Rejecting application {} by {}", applicationId, rejectedBy);
        
        BnplApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BnplApplicationException("Application not found"));
        
        if (!application.isPending()) {
            throw new BnplApplicationException("Application is not in pending status");
        }
        
        application.setStatus(ApplicationStatus.REJECTED);
        application.setDecision("REJECTED");
        application.setDecisionReason(reason);
        application.setDecisionDate(LocalDateTime.now());
        application.setDecisionBy(rejectedBy);
        
        application = applicationRepository.save(application);
        
        publishApplicationEvent(application, "APPLICATION_REJECTED");
        
        return mapToResponse(application);
    }
    
    /**
     * Gets user's applications
     */
    public Page<BnplApplicationResponse> getUserApplications(UUID userId, ApplicationStatus status, Pageable pageable) {
        Page<BnplApplication> applications;
        
        if (status != null) {
            applications = applicationRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            applications = applicationRepository.findByUserId(userId, pageable);
        }
        
        return applications.map(this::mapToResponse);
    }
    
    /**
     * Gets application details
     */
    public BnplApplicationResponse getApplication(UUID applicationId) {
        BnplApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BnplApplicationException("Application not found"));
        
        return mapToResponse(application);
    }
    
    /**
     * Calculates application terms based on credit assessment
     */
    private ApplicationTerms calculateApplicationTerms(BnplApplicationRequest request, CreditAssessment assessment) {
        ApplicationTerms terms = new ApplicationTerms();
        
        terms.financedAmount = request.getPurchaseAmount().subtract(request.getDownPayment());
        
        // Get recommended terms from assessment
        JsonNode recommendedTerms = assessment.getRecommendedTerms();
        
        // Determine installment count
        int maxInstallments = recommendedTerms.get("maxInstallments").asInt(6);
        terms.installmentCount = Math.min(request.getRequestedInstallments(), maxInstallments);
        
        // Apply interest if required
        BigDecimal interestRate = new BigDecimal(recommendedTerms.get("interestRate").asDouble(0.0));
        terms.interestRate = interestRate;
        
        // Calculate total amount with interest
        BigDecimal interestAmount = terms.financedAmount.multiply(interestRate);
        terms.totalAmount = terms.financedAmount.add(interestAmount);
        
        // Calculate installment amount
        terms.installmentAmount = terms.totalAmount.divide(
                new BigDecimal(terms.installmentCount), 
                2, 
                RoundingMode.HALF_UP
        );
        
        // Calculate payment dates
        terms.firstPaymentDate = LocalDate.now().plusDays(30);
        terms.finalPaymentDate = terms.firstPaymentDate.plusMonths(terms.installmentCount - 1);
        
        return terms;
    }
    
    private ApplicationTerms calculateApplicationTerms(BnplApplication application) {
        ApplicationTerms terms = new ApplicationTerms();
        terms.financedAmount = application.getFinancedAmount();
        terms.installmentCount = application.getInstallmentCount();
        terms.installmentAmount = application.getInstallmentAmount();
        terms.interestRate = application.getInterestRate();
        terms.totalAmount = application.getTotalAmount();
        terms.firstPaymentDate = LocalDate.now().plusDays(30);
        terms.finalPaymentDate = terms.firstPaymentDate.plusMonths(terms.installmentCount - 1);
        return terms;
    }
    
    /**
     * Creates installment schedule for approved application
     */
    private void createInstallmentSchedule(BnplApplication application, ApplicationTerms terms) {
        log.info("Creating installment schedule for application {}", application.getId());
        
        List<BnplInstallment> installments = new ArrayList<>();
        
        BigDecimal totalAmount = application.getTotalAmount();
        BigDecimal principalPerInstallment = application.getFinancedAmount()
                .divide(new BigDecimal(application.getInstallmentCount()), 2, RoundingMode.HALF_UP);
        BigDecimal interestPerInstallment = totalAmount.subtract(application.getFinancedAmount())
                .divide(new BigDecimal(application.getInstallmentCount()), 2, RoundingMode.HALF_UP);
        
        LocalDate dueDate = application.getFirstPaymentDate();
        
        for (int i = 1; i <= application.getInstallmentCount(); i++) {
            BnplInstallment installment = BnplInstallment.builder()
                    .application(application)
                    .userId(application.getUserId())
                    .installmentNumber(i)
                    .amount(application.getInstallmentAmount())
                    .principalAmount(principalPerInstallment)
                    .interestAmount(interestPerInstallment)
                    .dueDate(dueDate)
                    .status(BnplInstallment.InstallmentStatus.PENDING)
                    .build();
            
            installments.add(installment);
            dueDate = dueDate.plusMonths(1);
        }
        
        installmentRepository.saveAll(installments);
        
        // Update application status to active
        application.setStatus(ApplicationStatus.ACTIVE);
        applicationRepository.save(application);
    }
    
    private void validateApplicationRequest(BnplApplicationRequest request) {
        if (request.getPurchaseAmount().compareTo(MIN_PURCHASE_AMOUNT) < 0) {
            throw new BnplApplicationException("Purchase amount below minimum: " + MIN_PURCHASE_AMOUNT);
        }
        
        if (request.getPurchaseAmount().compareTo(MAX_PURCHASE_AMOUNT) > 0) {
            throw new BnplApplicationException("Purchase amount above maximum: " + MAX_PURCHASE_AMOUNT);
        }
        
        if (request.getDownPayment().compareTo(request.getPurchaseAmount()) >= 0) {
            throw new BnplApplicationException("Down payment cannot be greater than or equal to purchase amount");
        }
        
        if (request.getRequestedInstallments() < 2 || request.getRequestedInstallments() > 12) {
            throw new BnplApplicationException("Installment count must be between 2 and 12");
        }
    }
    
    private String generateApplicationNumber() {
        return "BNPL" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
    
    private BnplApplicationResponse createRejectedApplication(BnplApplicationRequest request, String reason) {
        BnplApplication application = BnplApplication.builder()
                .userId(request.getUserId())
                .merchantId(request.getMerchantId())
                .merchantName(request.getMerchantName())
                .orderId(request.getOrderId())
                .applicationNumber(generateApplicationNumber())
                .status(ApplicationStatus.REJECTED)
                .purchaseAmount(request.getPurchaseAmount())
                .currency(request.getCurrency())
                .downPayment(request.getDownPayment())
                .financedAmount(request.getPurchaseAmount().subtract(request.getDownPayment()))
                .decision("REJECTED")
                .decisionReason(reason)
                .decisionDate(LocalDateTime.now())
                .decisionBy("SYSTEM")
                .build();
        
        application = applicationRepository.save(application);
        publishApplicationEvent(application, "APPLICATION_REJECTED");
        
        return mapToResponse(application);
    }
    
    private void publishApplicationEvent(BnplApplication application, String eventType) {
        eventPublisher.publish("bnpl.application." + eventType.toLowerCase(), application);
    }
    
    private BnplApplicationResponse mapToResponse(BnplApplication application) {
        return BnplApplicationResponse.builder()
                .applicationId(application.getId())
                .applicationNumber(application.getApplicationNumber())
                .status(application.getStatus().name())
                .purchaseAmount(application.getPurchaseAmount())
                .financedAmount(application.getFinancedAmount())
                .installmentCount(application.getInstallmentCount())
                .installmentAmount(application.getInstallmentAmount())
                .totalAmount(application.getTotalAmount())
                .decision(application.getDecision())
                .decisionReason(application.getDecisionReason())
                .build();
    }
    
    private static class ApplicationTerms {
        BigDecimal financedAmount;
        Integer installmentCount;
        BigDecimal installmentAmount;
        BigDecimal interestRate;
        BigDecimal totalAmount;
        LocalDate firstPaymentDate;
        LocalDate finalPaymentDate;
    }
}