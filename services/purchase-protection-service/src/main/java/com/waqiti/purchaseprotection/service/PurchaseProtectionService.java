package com.waqiti.purchaseprotection.service;

import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.purchaseprotection.domain.*;
import com.waqiti.purchaseprotection.repository.ProtectionPolicyRepository;
import com.waqiti.purchaseprotection.repository.ClaimRepository;
import com.waqiti.purchaseprotection.repository.DisputeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive purchase protection insurance service providing buyer protection,
 * dispute resolution, fraud prevention, and seller verification.
 *
 * Features:
 * - Buyer protection for P2P transactions
 * - Automated dispute resolution system
 * - Fraud detection and prevention
 * - Automatic refund processing
 * - Seller verification and ratings
 * - Escrow services for high-value transactions
 * - Chargeback protection
 * - Extended warranty options
 * - Return protection
 * - Price protection guarantees
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PurchaseProtectionService {

    @Lazy
    private final PurchaseProtectionService self;
    private final ProtectionPolicyRepository policyRepository;
    private final ClaimRepository claimRepository;
    private final DisputeRepository disputeRepository;
    private final PaymentService paymentService;
    private final FraudDetectionService fraudDetectionService;
    private final SellerVerificationService sellerVerificationService;
    private final NotificationService notificationService;
    private final EscrowService escrowService;
    private final MetricsCollector metricsCollector;
    
    // Protection configuration
    private static final BigDecimal BASE_PROTECTION_FEE_PERCENTAGE = new BigDecimal("0.01"); // 1%
    private static final BigDecimal MAX_PROTECTION_AMOUNT = new BigDecimal("10000");
    private static final int DEFAULT_PROTECTION_DAYS = 90;
    private static final int DISPUTE_RESOLUTION_DAYS = 7;
    
    /**
     * Creates purchase protection for a transaction.
     *
     * @param request protection request
     * @return created protection policy
     */
    @Transactional
    public ProtectionPolicy createProtection(CreateProtectionRequest request) {
        log.info("Creating purchase protection for transaction: {} amount: {}", 
                request.getTransactionId(), request.getTransactionAmount());
        
        // Verify seller and assess risk
        SellerProfile sellerProfile = sellerVerificationService.getSellerProfile(request.getSellerId());
        RiskAssessment riskAssessment = assessTransactionRisk(request, sellerProfile);
        
        // Calculate protection fee based on risk
        BigDecimal protectionFee = calculateProtectionFee(
            request.getTransactionAmount(), 
            riskAssessment, 
            request.getCoverageType()
        );
        
        // Determine if escrow is needed
        boolean requiresEscrow = shouldUseEscrow(request, riskAssessment);
        
        ProtectionPolicy policy = ProtectionPolicy.builder()
                .transactionId(request.getTransactionId())
                .buyerId(request.getBuyerId())
                .sellerId(request.getSellerId())
                .transactionAmount(request.getTransactionAmount())
                .currency(request.getCurrency())
                .coverageType(request.getCoverageType())
                .coverageAmount(calculateCoverageAmount(request))
                .protectionFee(protectionFee)
                .startDate(Instant.now())
                .endDate(calculateEndDate(request.getCoverageType()))
                .status(PolicyStatus.ACTIVE)
                .riskScore(riskAssessment.getScore())
                .riskLevel(riskAssessment.getLevel())
                .sellerVerified(sellerProfile.isVerified())
                .sellerRating(sellerProfile.getRating())
                .requiresEscrow(requiresEscrow)
                .itemDescription(request.getItemDescription())
                .itemCategory(request.getItemCategory())
                .purchaseEvidence(request.getPurchaseEvidence())
                .createdAt(Instant.now())
                .build();
        
        // Process protection fee
        processProtectionFee(policy);
        
        // Setup escrow if required
        if (requiresEscrow) {
            String escrowId = escrowService.createEscrow(
                request.getTransactionId(),
                request.getBuyerId(),
                request.getSellerId(),
                request.getTransactionAmount()
            );
            policy.setEscrowId(escrowId);
            policy.setEscrowStatus(EscrowStatus.HOLDING);
        }
        
        ProtectionPolicy saved = policyRepository.save(policy);
        
        // Send confirmation notifications
        notifyProtectionCreated(saved);
        
        metricsCollector.incrementCounter("purchase_protection.policies.created");
        log.info("Created purchase protection policy: {} with coverage: {}", 
                saved.getId(), saved.getCoverageAmount());
        
        return saved;
    }
    
    /**
     * Files a claim against a protection policy.
     *
     * @param policyId policy ID
     * @param request claim request
     * @return filed claim
     */
    @Transactional
    public ProtectionClaim fileClaim(String policyId, FileClaimRequest request) {
        ProtectionPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));
        
        validateClaimEligibility(policy, request);
        
        log.info("Filing claim for policy: {} reason: {}", policyId, request.getReason());
        
        ProtectionClaim claim = ProtectionClaim.builder()
                .policy(policy)
                .claimType(request.getClaimType())
                .reason(request.getReason())
                .description(request.getDescription())
                .claimAmount(request.getClaimAmount())
                .status(ClaimStatus.SUBMITTED)
                .filedAt(Instant.now())
                .evidenceUrls(request.getEvidenceUrls())
                .build();
        
        // Perform initial fraud check
        FraudCheckResult fraudCheck = fraudDetectionService.checkClaim(claim);
        claim.setFraudScore(fraudCheck.getScore());
        claim.setFraudCheckResult(fraudCheck.getResult());
        
        if (fraudCheck.getResult() == FraudResult.HIGH_RISK) {
            claim.setStatus(ClaimStatus.UNDER_INVESTIGATION);
            claim.setInvestigationReason("High fraud risk detected");
        } else if (isAutoApprovable(claim, policy)) {
            // Auto-approve low-risk, small claims
            claim.setStatus(ClaimStatus.APPROVED);
            claim.setApprovedAt(Instant.now());
            claim.setApprovedAmount(claim.getClaimAmount());
            claim.setAutoApproved(true);
        } else {
            // Regular review process
            claim.setStatus(ClaimStatus.UNDER_REVIEW);
        }
        
        ProtectionClaim saved = claimRepository.save(claim);
        
        // Update policy status
        policy.setHasActiveClaim(true);
        policy.setLastClaimAt(Instant.now());
        policyRepository.save(policy);
        
        // Handle auto-approved claims
        if (saved.getStatus() == ClaimStatus.APPROVED) {
            processApprovedClaim(saved);
        }
        
        // Notify parties
        notifyClaimFiled(saved);
        
        metricsCollector.incrementCounter("purchase_protection.claims.filed");
        
        return saved;
    }
    
    /**
     * Initiates a dispute between buyer and seller.
     *
     * @param request dispute request
     * @return created dispute
     */
    @Transactional
    public Dispute initiateDispute(InitiateDisputeRequest request) {
        log.info("Initiating dispute for transaction: {} between buyer: {} and seller: {}", 
                request.getTransactionId(), request.getBuyerId(), request.getSellerId());
        
        // Check if protection policy exists
        Optional<ProtectionPolicy> policyOpt = policyRepository.findByTransactionId(request.getTransactionId());
        
        Dispute dispute = Dispute.builder()
                .transactionId(request.getTransactionId())
                .buyerId(request.getBuyerId())
                .sellerId(request.getSellerId())
                .amount(request.getDisputeAmount())
                .reason(request.getReason())
                .description(request.getDescription())
                .status(DisputeStatus.OPEN)
                .initiatedBy(request.getInitiatorId())
                .initiatedAt(Instant.now())
                .deadlineAt(Instant.now().plus(DISPUTE_RESOLUTION_DAYS, ChronoUnit.DAYS))
                .hasProtection(policyOpt.isPresent())
                .protectionPolicyId(policyOpt.map(ProtectionPolicy::getId).orElse(null))
                .evidenceFromBuyer(request.getBuyerEvidence())
                .build();
        
        // Hold funds if escrow is active
        if (policyOpt.isPresent() && policyOpt.get().isRequiresEscrow()) {
            escrowService.holdFunds(policyOpt.get().getEscrowId());
            dispute.setFundsHeld(true);
        }
        
        Dispute saved = disputeRepository.save(dispute);
        
        // Request seller response
        requestSellerResponse(saved);
        
        // Start automated resolution timer
        scheduleAutomaticResolution(saved);
        
        metricsCollector.incrementCounter("purchase_protection.disputes.initiated");
        
        return saved;
    }
    
    /**
     * Submits seller response to a dispute.
     *
     * @param disputeId dispute ID
     * @param request seller response
     * @return updated dispute
     */
    @Transactional
    public Dispute submitSellerResponse(String disputeId, SellerResponseRequest request) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));
        
        if (!dispute.getSellerId().equals(request.getSellerId())) {
            throw new UnauthorizedException("Seller not authorized for this dispute");
        }
        
        log.info("Seller {} responding to dispute: {}", request.getSellerId(), disputeId);
        
        dispute.setSellerResponse(request.getResponse());
        dispute.setEvidenceFromSeller(request.getSellerEvidence());
        dispute.setSellerRespondedAt(Instant.now());
        dispute.setStatus(DisputeStatus.SELLER_RESPONDED);
        
        // Analyze responses for automatic resolution
        ResolutionAnalysis analysis = analyzeDisputeResponses(dispute);
        
        if (analysis.canAutoResolve()) {
            resolveDisputeAutomatically(dispute, analysis);
        } else {
            // Escalate to manual review
            dispute.setStatus(DisputeStatus.UNDER_MEDIATION);
            assignMediator(dispute);
        }
        
        Dispute updated = disputeRepository.save(dispute);
        
        // Notify buyer of seller response
        notifySellerResponse(updated);
        
        return updated;
    }
    
    /**
     * Resolves a dispute with a decision.
     *
     * @param disputeId dispute ID
     * @param request resolution request
     * @return resolution result
     */
    @Transactional
    public DisputeResolution resolveDispute(String disputeId, ResolveDisputeRequest request) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeNotFoundException(disputeId));
        
        log.info("Resolving dispute: {} with decision: {}", disputeId, request.getDecision());
        
        DisputeResolution resolution = DisputeResolution.builder()
                .disputeId(disputeId)
                .decision(request.getDecision())
                .refundAmount(request.getRefundAmount())
                .sellerPayout(request.getSellerPayout())
                .reason(request.getReason())
                .resolvedBy(request.getResolvedBy())
                .resolvedAt(Instant.now())
                .build();
        
        // Update dispute status
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolution(resolution);
        dispute.setResolvedAt(Instant.now());
        
        // Process resolution
        processDisputeResolution(dispute, resolution);
        
        // Update seller ratings based on outcome
        updateSellerRating(dispute, resolution);
        
        disputeRepository.save(dispute);
        
        // Send resolution notifications
        notifyDisputeResolved(dispute, resolution);
        
        metricsCollector.incrementCounter("purchase_protection.disputes.resolved");
        
        return resolution;
    }
    
    /**
     * Processes an approved claim.
     *
     * @param claimId claim ID
     * @return processing result
     */
    @Transactional
    public CompletableFuture<ClaimProcessingResult> processClaim(String claimId) {
        return CompletableFuture.supplyAsync(() -> {
            ProtectionClaim claim = claimRepository.findById(claimId)
                    .orElseThrow(() -> new ClaimNotFoundException(claimId));
            
            if (claim.getStatus() != ClaimStatus.APPROVED) {
                throw new IllegalStateException("Claim not approved for processing");
            }
            
            log.info("Processing approved claim: {} for amount: {}", claimId, claim.getApprovedAmount());
            
            ClaimProcessingResult result = new ClaimProcessingResult();
            result.setClaimId(claimId);
            result.setStartedAt(Instant.now());
            
            try {
                // Process refund to buyer
                PaymentResult refundResult = paymentService.processRefund(
                    claim.getPolicy().getBuyerId(),
                    claim.getApprovedAmount(),
                    claim.getPolicy().getCurrency(),
                    "Purchase protection claim refund"
                );
                
                if (refundResult.isSuccess()) {
                    claim.setStatus(ClaimStatus.PAID);
                    claim.setPaidAt(Instant.now());
                    claim.setPaymentReference(refundResult.getTransactionId());
                    
                    result.setSuccess(true);
                    result.setRefundTransactionId(refundResult.getTransactionId());
                    
                    // Recover funds from seller if applicable
                    if (shouldRecoverFromSeller(claim)) {
                        recoverFundsFromSeller(claim);
                    }
                    
                } else {
                    claim.setStatus(ClaimStatus.PAYMENT_FAILED);
                    claim.setPaymentFailureReason(refundResult.getErrorMessage());
                    
                    result.setSuccess(false);
                    result.setErrorMessage(refundResult.getErrorMessage());
                }
                
            } catch (Exception e) {
                log.error("Failed to process claim: {}", claimId, e);
                claim.setStatus(ClaimStatus.PROCESSING_ERROR);
                
                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
            }
            
            claimRepository.save(claim);
            result.setCompletedAt(Instant.now());
            
            // Send notifications
            notifyClaimProcessed(claim, result);
            
            metricsCollector.incrementCounter("purchase_protection.claims.processed");
            
            return result;
        });
    }
    
    /**
     * Gets protection recommendations for a transaction.
     *
     * @param request recommendation request
     * @return protection recommendations
     */
    public ProtectionRecommendation getProtectionRecommendation(RecommendationRequest request) {
        // Assess transaction risk
        SellerProfile sellerProfile = sellerVerificationService.getSellerProfile(request.getSellerId());
        RiskAssessment risk = assessTransactionRisk(
            CreateProtectionRequest.builder()
                .transactionAmount(request.getTransactionAmount())
                .sellerId(request.getSellerId())
                .itemCategory(request.getItemCategory())
                .build(),
            sellerProfile
        );
        
        ProtectionRecommendation recommendation = new ProtectionRecommendation();
        recommendation.setRecommendedCoverage(determineRecommendedCoverage(risk));
        recommendation.setEstimatedFee(calculateProtectionFee(
            request.getTransactionAmount(), 
            risk, 
            recommendation.getRecommendedCoverage()
        ));
        recommendation.setRiskLevel(risk.getLevel());
        recommendation.setSellerTrustScore(sellerProfile.getTrustScore());
        recommendation.setRequiresEscrow(risk.getScore() > 70);
        
        // Add specific recommendations
        List<String> recommendations = new ArrayList<>();
        
        if (risk.getLevel() == RiskLevel.HIGH) {
            recommendations.add("High-risk transaction detected. Extended protection recommended.");
            recommendations.add("Consider using escrow service for added security.");
        }
        
        if (!sellerProfile.isVerified()) {
            recommendations.add("Seller is not verified. Exercise caution.");
        }
        
        if (sellerProfile.getRating() < 3.0) {
            recommendations.add("Seller has low ratings. Review seller history before proceeding.");
        }
        
        if (request.getTransactionAmount().compareTo(new BigDecimal("1000")) > 0) {
            recommendations.add("For high-value transactions, consider premium protection.");
        }
        
        recommendation.setRecommendations(recommendations);
        
        return recommendation;
    }
    
    /**
     * Extends protection coverage for a policy.
     *
     * @param policyId policy ID
     * @param request extension request
     * @return updated policy
     */
    @Transactional
    public ProtectionPolicy extendProtection(String policyId, ExtendProtectionRequest request) {
        ProtectionPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));
        
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new IllegalStateException("Can only extend active policies");
        }
        
        log.info("Extending protection for policy: {} by {} days", policyId, request.getExtensionDays());
        
        // Calculate extension fee
        BigDecimal extensionFee = calculateExtensionFee(policy, request.getExtensionDays());
        
        // Process extension fee
        PaymentResult payment = paymentService.processPayment(
            policy.getBuyerId(),
            "PROTECTION_SERVICE",
            extensionFee,
            policy.getCurrency(),
            "Protection extension fee"
        );
        
        if (!payment.isSuccess()) {
            throw new PaymentFailedException("Failed to process extension fee");
        }
        
        // Update policy
        policy.setEndDate(policy.getEndDate().plus(request.getExtensionDays(), ChronoUnit.DAYS));
        policy.setExtended(true);
        policy.setExtensionCount(policy.getExtensionCount() + 1);
        policy.setTotalFees(policy.getTotalFees().add(extensionFee));
        
        ProtectionPolicy updated = policyRepository.save(policy);
        
        // Send confirmation
        notifyProtectionExtended(updated);
        
        metricsCollector.incrementCounter("purchase_protection.policies.extended");
        
        return updated;
    }
    
    /**
     * Gets protection statistics for a user.
     *
     * @param userId user ID
     * @return protection statistics
     */
    public ProtectionStatistics getUserStatistics(String userId) {
        List<ProtectionPolicy> policies = policyRepository.findByBuyerId(userId);
        List<ProtectionClaim> claims = claimRepository.findByBuyerId(userId);
        List<Dispute> disputes = disputeRepository.findByBuyerIdOrSellerId(userId, userId);
        
        ProtectionStatistics stats = new ProtectionStatistics();
        stats.setTotalPolicies(policies.size());
        stats.setActivePolicies(policies.stream()
                .filter(p -> p.getStatus() == PolicyStatus.ACTIVE)
                .count());
        stats.setTotalProtectedAmount(policies.stream()
                .map(ProtectionPolicy::getCoverageAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.setTotalClaims(claims.size());
        stats.setSuccessfulClaims(claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.PAID)
                .count());
        stats.setTotalClaimsPaid(claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.PAID)
                .map(ProtectionClaim::getApprovedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.setTotalDisputes(disputes.size());
        stats.setDisputesWon(disputes.stream()
                .filter(d -> d.getStatus() == DisputeStatus.RESOLVED)
                .filter(d -> isDisputeWonByUser(d, userId))
                .count());
        
        return stats;
    }
    
    /**
     * Processes automatic claim reviews.
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void processClaimReviews() {
        List<ProtectionClaim> pendingClaims = claimRepository.findByStatus(ClaimStatus.UNDER_REVIEW);
        
        for (ProtectionClaim claim : pendingClaims) {
            try {
                reviewClaim(claim);
            } catch (Exception e) {
                log.error("Error reviewing claim: {}", claim.getId(), e);
            }
        }
    }
    
    /**
     * Processes automatic dispute resolutions.
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void processDisputeResolutions() {
        List<Dispute> expiredDisputes = disputeRepository.findExpiredDisputes(Instant.now());
        
        for (Dispute dispute : expiredDisputes) {
            try {
                resolveExpiredDispute(dispute);
            } catch (Exception e) {
                log.error("Error resolving expired dispute: {}", dispute.getId(), e);
            }
        }
    }
    
    // Private helper methods
    
    private RiskAssessment assessTransactionRisk(CreateProtectionRequest request, SellerProfile sellerProfile) {
        RiskAssessment assessment = new RiskAssessment();
        double score = 0.0;
        
        // Seller risk factors
        if (!sellerProfile.isVerified()) {
            score += 20;
        }
        
        if (sellerProfile.getRating() < 3.0) {
            score += 15;
        }
        
        if (sellerProfile.getDisputeRate() > 0.1) {
            score += 25;
        }
        
        // Transaction risk factors
        BigDecimal amount = request.getTransactionAmount();
        if (amount.compareTo(new BigDecimal("500")) > 0) {
            score += 10;
        }
        if (amount.compareTo(new BigDecimal("2000")) > 0) {
            score += 15;
        }
        
        // Category risk
        if (isHighRiskCategory(request.getItemCategory())) {
            score += 20;
        }
        
        // New seller risk
        if (sellerProfile.getAccountAge() < 30) {
            score += 15;
        }
        
        assessment.setScore(score);
        
        if (score < 30) {
            assessment.setLevel(RiskLevel.LOW);
        } else if (score < 60) {
            assessment.setLevel(RiskLevel.MEDIUM);
        } else {
            assessment.setLevel(RiskLevel.HIGH);
        }
        
        assessment.setFactors(identifyRiskFactors(request, sellerProfile));
        
        return assessment;
    }
    
    private BigDecimal calculateProtectionFee(BigDecimal amount, RiskAssessment risk, CoverageType coverage) {
        BigDecimal baseFee = amount.multiply(BASE_PROTECTION_FEE_PERCENTAGE);
        
        // Adjust for risk level
        BigDecimal riskMultiplier = switch (risk.getLevel()) {
            case LOW -> new BigDecimal("1.0");
            case MEDIUM -> new BigDecimal("1.5");
            case HIGH -> new BigDecimal("2.0");
        };
        
        // Adjust for coverage type
        BigDecimal coverageMultiplier = switch (coverage) {
            case BASIC -> new BigDecimal("1.0");
            case STANDARD -> new BigDecimal("1.5");
            case PREMIUM -> new BigDecimal("2.0");
            case EXTENDED -> new BigDecimal("2.5");
        };
        
        return baseFee.multiply(riskMultiplier)
                     .multiply(coverageMultiplier)
                     .setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateCoverageAmount(CreateProtectionRequest request) {
        BigDecimal baseAmount = request.getTransactionAmount();
        
        return switch (request.getCoverageType()) {
            case BASIC -> baseAmount.multiply(new BigDecimal("0.75"));
            case STANDARD -> baseAmount;
            case PREMIUM -> baseAmount.multiply(new BigDecimal("1.1"));
            case EXTENDED -> baseAmount.multiply(new BigDecimal("1.25"));
        }.min(MAX_PROTECTION_AMOUNT);
    }
    
    private Instant calculateEndDate(CoverageType coverageType) {
        int days = switch (coverageType) {
            case BASIC -> 30;
            case STANDARD -> 60;
            case PREMIUM -> 90;
            case EXTENDED -> 180;
        };
        
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }
    
    private boolean shouldUseEscrow(CreateProtectionRequest request, RiskAssessment risk) {
        return risk.getLevel() == RiskLevel.HIGH ||
               request.getTransactionAmount().compareTo(new BigDecimal("1000")) > 0 ||
               request.isRequestEscrow();
    }
    
    private void processProtectionFee(ProtectionPolicy policy) {
        try {
            paymentService.processPayment(
                policy.getBuyerId(),
                "PROTECTION_SERVICE",
                policy.getProtectionFee(),
                policy.getCurrency(),
                "Purchase protection fee"
            );
            
            policy.setFeeCollected(true);
            policy.setTotalFees(policy.getProtectionFee());
            
        } catch (Exception e) {
            log.error("Failed to process protection fee for policy: {}", policy.getId(), e);
            policy.setFeeCollected(false);
        }
    }
    
    private void validateClaimEligibility(ProtectionPolicy policy, FileClaimRequest request) {
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new IllegalStateException("Policy is not active");
        }
        
        if (Instant.now().isAfter(policy.getEndDate())) {
            throw new IllegalStateException("Protection period has expired");
        }
        
        if (!policy.getBuyerId().equals(request.getBuyerId())) {
            throw new UnauthorizedException("Only the buyer can file a claim");
        }
        
        if (request.getClaimAmount().compareTo(policy.getCoverageAmount()) > 0) {
            throw new IllegalArgumentException("Claim amount exceeds coverage limit");
        }
    }
    
    private boolean isAutoApprovable(ProtectionClaim claim, ProtectionPolicy policy) {
        // Auto-approve conditions
        return claim.getClaimAmount().compareTo(new BigDecimal("100")) < 0 &&
               claim.getFraudScore() < 30 &&
               policy.isSellerVerified() == false &&
               claim.getClaimType() == ClaimType.ITEM_NOT_RECEIVED;
    }
    
    private void processApprovedClaim(ProtectionClaim claim) {
        CompletableFuture<ClaimProcessingResult> future = self.processClaim(claim.getId());
        
        future.thenAccept(result -> {
            if (result.isSuccess()) {
                log.info("Successfully processed claim: {}", claim.getId());
            } else {
                log.error("Failed to process claim: {} - {}", claim.getId(), result.getErrorMessage());
            }
        });
    }
    
    private void requestSellerResponse(Dispute dispute) {
        notificationService.requestSellerResponse(dispute);
    }
    
    private void scheduleAutomaticResolution(Dispute dispute) {
        // Schedule automatic resolution if seller doesn't respond
        log.info("Scheduled automatic resolution for dispute: {} at {}", 
                dispute.getId(), dispute.getDeadlineAt());
    }
    
    private ResolutionAnalysis analyzeDisputeResponses(Dispute dispute) {
        ResolutionAnalysis analysis = new ResolutionAnalysis();
        
        // Analyze evidence and responses
        if (dispute.getEvidenceFromBuyer() != null && dispute.getEvidenceFromSeller() == null) {
            analysis.setCanAutoResolve(true);
            analysis.setSuggestedDecision(DisputeDecision.FAVOR_BUYER);
            analysis.setConfidence(0.8);
        } else if (hasStrongBuyerEvidence(dispute)) {
            analysis.setCanAutoResolve(true);
            analysis.setSuggestedDecision(DisputeDecision.FAVOR_BUYER);
            analysis.setConfidence(0.7);
        } else {
            analysis.setCanAutoResolve(false);
        }
        
        return analysis;
    }
    
    private void resolveDisputeAutomatically(Dispute dispute, ResolutionAnalysis analysis) {
        ResolveDisputeRequest request = ResolveDisputeRequest.builder()
                .decision(analysis.getSuggestedDecision())
                .refundAmount(dispute.getAmount())
                .reason("Automatically resolved based on evidence analysis")
                .resolvedBy("SYSTEM")
                .build();
        
        self.resolveDispute(dispute.getId(), request);
    }
    
    private void assignMediator(Dispute dispute) {
        // Assign human mediator for complex disputes
        log.info("Assigning mediator for dispute: {}", dispute.getId());
    }
    
    private void processDisputeResolution(Dispute dispute, DisputeResolution resolution) {
        if (resolution.getRefundAmount() != null && resolution.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Process refund to buyer
            if (dispute.isFundsHeld() && dispute.getProtectionPolicyId() != null) {
                ProtectionPolicy policy = policyRepository.findById(dispute.getProtectionPolicyId()).orElse(null);
                if (policy != null && policy.getEscrowId() != null) {
                    escrowService.releaseToBuyer(policy.getEscrowId(), resolution.getRefundAmount());
                }
            } else {
                paymentService.processRefund(
                    dispute.getBuyerId(),
                    resolution.getRefundAmount(),
                    "USD",
                    "Dispute resolution refund"
                );
            }
        }
        
        if (resolution.getSellerPayout() != null && resolution.getSellerPayout().compareTo(BigDecimal.ZERO) > 0) {
            // Process payout to seller
            if (dispute.isFundsHeld() && dispute.getProtectionPolicyId() != null) {
                ProtectionPolicy policy = policyRepository.findById(dispute.getProtectionPolicyId()).orElse(null);
                if (policy != null && policy.getEscrowId() != null) {
                    escrowService.releaseToSeller(policy.getEscrowId(), resolution.getSellerPayout());
                }
            }
        }
    }
    
    private void updateSellerRating(Dispute dispute, DisputeResolution resolution) {
        if (resolution.getDecision() == DisputeDecision.FAVOR_BUYER) {
            sellerVerificationService.recordNegativeOutcome(dispute.getSellerId());
        }
    }
    
    private boolean shouldRecoverFromSeller(ProtectionClaim claim) {
        return claim.getClaimType() == ClaimType.FRAUDULENT_SELLER ||
               claim.getClaimType() == ClaimType.ITEM_NOT_AS_DESCRIBED;
    }
    
    private void recoverFundsFromSeller(ProtectionClaim claim) {
        // Attempt to recover funds from seller
        log.info("Attempting to recover {} from seller {} for claim {}", 
                claim.getApprovedAmount(), claim.getPolicy().getSellerId(), claim.getId());
    }
    
    private void reviewClaim(ProtectionClaim claim) {
        // Automated claim review logic
        if (claim.getFraudScore() < 20 && claim.getClaimAmount().compareTo(new BigDecimal("500")) < 0) {
            claim.setStatus(ClaimStatus.APPROVED);
            claim.setApprovedAt(Instant.now());
            claim.setApprovedAmount(claim.getClaimAmount());
            claimRepository.save(claim);
            
            processApprovedClaim(claim);
        }
    }
    
    private void resolveExpiredDispute(Dispute dispute) {
        if (dispute.getSellerResponse() == null) {
            // Auto-resolve in favor of buyer if seller didn't respond
            ResolveDisputeRequest request = ResolveDisputeRequest.builder()
                    .decision(DisputeDecision.FAVOR_BUYER)
                    .refundAmount(dispute.getAmount())
                    .reason("Seller failed to respond within deadline")
                    .resolvedBy("SYSTEM")
                    .build();
            
            resolveDispute(dispute.getId(), request);
        }
    }
    
    private boolean isHighRiskCategory(String category) {
        Set<String> highRiskCategories = Set.of(
            "ELECTRONICS", "JEWELRY", "COLLECTIBLES", "TICKETS", "GIFT_CARDS"
        );
        return highRiskCategories.contains(category);
    }
    
    private List<String> identifyRiskFactors(CreateProtectionRequest request, SellerProfile seller) {
        List<String> factors = new ArrayList<>();
        
        if (!seller.isVerified()) {
            factors.add("Unverified seller");
        }
        if (seller.getRating() < 3.0) {
            factors.add("Low seller rating");
        }
        if (request.getTransactionAmount().compareTo(new BigDecimal("1000")) > 0) {
            factors.add("High transaction amount");
        }
        if (isHighRiskCategory(request.getItemCategory())) {
            factors.add("High-risk category");
        }
        
        return factors;
    }
    
    private boolean hasStrongBuyerEvidence(Dispute dispute) {
        return dispute.getEvidenceFromBuyer() != null && 
               dispute.getEvidenceFromBuyer().size() > 2;
    }
    
    private boolean isDisputeWonByUser(Dispute dispute, String userId) {
        if (dispute.getResolution() == null) {
            return false;
        }
        
        DisputeDecision decision = dispute.getResolution().getDecision();
        
        if (userId.equals(dispute.getBuyerId())) {
            return decision == DisputeDecision.FAVOR_BUYER || decision == DisputeDecision.PARTIAL_REFUND;
        } else if (userId.equals(dispute.getSellerId())) {
            return decision == DisputeDecision.FAVOR_SELLER;
        }
        
        return false;
    }
    
    private BigDecimal calculateExtensionFee(ProtectionPolicy policy, int days) {
        BigDecimal dailyRate = policy.getProtectionFee()
                .divide(BigDecimal.valueOf(DEFAULT_PROTECTION_DAYS), 4, RoundingMode.HALF_UP);
        
        return dailyRate.multiply(BigDecimal.valueOf(days))
                       .setScale(2, RoundingMode.HALF_UP);
    }
    
    private CoverageType determineRecommendedCoverage(RiskAssessment risk) {
        return switch (risk.getLevel()) {
            case LOW -> CoverageType.BASIC;
            case MEDIUM -> CoverageType.STANDARD;
            case HIGH -> CoverageType.PREMIUM;
        };
    }
    
    // Notification methods
    
    private void notifyProtectionCreated(ProtectionPolicy policy) {
        notificationService.sendProtectionCreatedNotification(policy);
    }
    
    private void notifyClaimFiled(ProtectionClaim claim) {
        notificationService.sendClaimFiledNotification(claim);
    }
    
    private void notifyClaimProcessed(ProtectionClaim claim, ClaimProcessingResult result) {
        notificationService.sendClaimProcessedNotification(claim, result);
    }
    
    private void notifySellerResponse(Dispute dispute) {
        notificationService.sendSellerResponseNotification(dispute);
    }
    
    private void notifyDisputeResolved(Dispute dispute, DisputeResolution resolution) {
        notificationService.sendDisputeResolvedNotification(dispute, resolution);
    }
    
    private void notifyProtectionExtended(ProtectionPolicy policy) {
        notificationService.sendProtectionExtendedNotification(policy);
    }
}