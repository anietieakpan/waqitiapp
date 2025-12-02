package com.waqiti.lending.service;

import com.waqiti.lending.domain.LoanApplication;
import com.waqiti.lending.domain.enums.ApplicationStatus;
import com.waqiti.lending.domain.enums.LoanType;
import com.waqiti.lending.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Loan Application Service
 * Handles loan application lifecycle management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;

    /**
     * Submit a new loan application
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanApplication submitApplication(LoanApplication application) {
        // Generate application ID if not present
        if (application.getApplicationId() == null || application.getApplicationId().isEmpty()) {
            application.setApplicationId(generateApplicationId());
        }

        // Set initial status
        application.setApplicationStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(Instant.now());

        // Set expiration (30 days from approval)
        application.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));

        LoanApplication saved = loanApplicationRepository.save(application);
        log.info("Loan application submitted: {} for borrower: {}",
                saved.getApplicationId(), saved.getBorrowerId());

        return saved;
    }

    /**
     * Find application by ID
     */
    @Transactional(readOnly = true)
    public LoanApplication findByApplicationId(String applicationId) {
        return loanApplicationRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
    }

    /**
     * Get all applications for a borrower
     */
    @Transactional(readOnly = true)
    public List<LoanApplication> findByBorrower(UUID borrowerId) {
        return loanApplicationRepository.findByBorrowerIdOrderBySubmittedAtDesc(borrowerId);
    }

    /**
     * Get applications by status
     */
    @Transactional(readOnly = true)
    public Page<LoanApplication> findByStatus(ApplicationStatus status, Pageable pageable) {
        return loanApplicationRepository.findByApplicationStatus(status, pageable);
    }

    /**
     * Approve application
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public LoanApplication approveApplication(String applicationId,
                                              BigDecimal approvedAmount,
                                              Integer approvedTermMonths,
                                              BigDecimal approvedInterestRate,
                                              String reviewedBy) {
        LoanApplication application = findByApplicationId(applicationId);

        if (application.getApplicationStatus() != ApplicationStatus.SUBMITTED &&
            application.getApplicationStatus() != ApplicationStatus.UNDER_REVIEW &&
            application.getApplicationStatus() != ApplicationStatus.MANUAL_REVIEW) {
            throw new IllegalStateException("Application cannot be approved in current status: " +
                    application.getApplicationStatus());
        }

        application.approve(approvedAmount, approvedTermMonths, approvedInterestRate, reviewedBy);

        LoanApplication saved = loanApplicationRepository.save(application);
        log.info("Loan application approved: {} for borrower: {} with amount: {}",
                saved.getApplicationId(), saved.getBorrowerId(), approvedAmount);

        return saved;
    }

    /**
     * Reject application
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public LoanApplication rejectApplication(String applicationId, String reason, String reviewedBy) {
        LoanApplication application = findByApplicationId(applicationId);

        if (application.getApplicationStatus() == ApplicationStatus.APPROVED ||
            application.getApplicationStatus() == ApplicationStatus.REJECTED) {
            throw new IllegalStateException("Application cannot be rejected in current status: " +
                    application.getApplicationStatus());
        }

        application.reject(reason, reviewedBy);

        LoanApplication saved = loanApplicationRepository.save(application);
        log.info("Loan application rejected: {} for borrower: {} - Reason: {}",
                saved.getApplicationId(), saved.getBorrowerId(), reason);

        return saved;
    }

    /**
     * Mark application for manual review
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanApplication markForManualReview(String applicationId, String reason) {
        LoanApplication application = findByApplicationId(applicationId);
        application.markForManualReview(reason);

        LoanApplication saved = loanApplicationRepository.save(application);
        log.info("Loan application marked for manual review: {} - Reason: {}",
                saved.getApplicationId(), reason);

        return saved;
    }

    /**
     * Update application status
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanApplication updateStatus(String applicationId, ApplicationStatus newStatus) {
        LoanApplication application = findByApplicationId(applicationId);
        application.setApplicationStatus(newStatus);

        LoanApplication saved = loanApplicationRepository.save(application);
        log.info("Application status updated: {} to {}", applicationId, newStatus);

        return saved;
    }

    /**
     * Get pending applications
     */
    @Transactional(readOnly = true)
    public List<LoanApplication> getPendingApplications() {
        return loanApplicationRepository.findPendingApplications();
    }

    /**
     * Get applications awaiting decision
     */
    @Transactional(readOnly = true)
    public List<LoanApplication> getApplicationsAwaitingDecision() {
        return loanApplicationRepository.findApplicationsAwaitingDecision();
    }

    /**
     * Handle expired applications
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int handleExpiredApplications() {
        List<LoanApplication> expired = loanApplicationRepository.findExpiredApprovedApplications(Instant.now());

        int count = 0;
        for (LoanApplication app : expired) {
            app.setApplicationStatus(ApplicationStatus.EXPIRED);
            loanApplicationRepository.save(app);
            count++;
        }

        if (count > 0) {
            log.info("Marked {} expired applications", count);
        }

        return count;
    }

    /**
     * Check if borrower has active applications
     */
    @Transactional(readOnly = true)
    public boolean hasActiveApplications(UUID borrowerId) {
        List<LoanApplication> active = loanApplicationRepository.findByBorrowerIdAndApplicationStatus(
                borrowerId, ApplicationStatus.SUBMITTED);
        return !active.isEmpty();
    }

    /**
     * Generate unique application ID
     */
    private String generateApplicationId() {
        return "APP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Get application statistics
     */
    @Transactional(readOnly = true)
    public List<Object[]> getApplicationStatistics() {
        return loanApplicationRepository.getApplicationStatistics();
    }
}
