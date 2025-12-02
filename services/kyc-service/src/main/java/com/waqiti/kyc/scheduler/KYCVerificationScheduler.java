package com.waqiti.kyc.scheduler;

import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.kyc.repository.KYCVerificationRepository;
import com.waqiti.kyc.service.KYCProviderService;
import com.waqiti.kyc.service.KYCVerificationService;
import com.waqiti.kyc.service.NotificationService;
import com.waqiti.common.event.EventPublisher;
import com.waqiti.kyc.event.KYCVerificationExpiredEvent;
import com.waqiti.kyc.event.KYCVerificationReminderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Scheduled tasks for KYC verification monitoring and maintenance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KYCVerificationScheduler {
    
    private final KYCVerificationRepository verificationRepository;
    private final KYCVerificationService verificationService;
    private final KYCProviderService providerService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    /**
     * Check status of in-progress verifications every 5 minutes
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional
    public void checkInProgressVerifications() {
        log.info("Starting in-progress verification status check");
        
        int pageSize = 100;
        int page = 0;
        int processed = 0;
        
        try {
            Page<KYCVerification> verifications;
            do {
                verifications = verificationRepository.findByStatus(
                        KYCVerification.Status.IN_PROGRESS,
                        PageRequest.of(page++, pageSize)
                );
                
                List<CompletableFuture<Void>> futures = verifications.getContent().stream()
                        .map(verification -> CompletableFuture.runAsync(() -> 
                                checkVerificationStatus(verification), executorService))
                        .collect(Collectors.toList());
                
                // Wait for all checks to complete
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(5, java.util.concurrent.TimeUnit.MINUTES);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("KYC verification status check timed out after 5 minutes for page: {}", page, e);
                    futures.forEach(f -> f.cancel(true));
                } catch (java.util.concurrent.ExecutionException e) {
                    log.error("KYC verification status check execution failed for page: {}", page, e.getCause());
                } catch (java.util.concurrent.InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("KYC verification status check interrupted for page: {}", page, e);
                }

                processed += verifications.getNumberOfElements();
                
            } while (verifications.hasNext());
            
            log.info("Completed in-progress verification check. Processed: {} verifications", processed);
            
        } catch (Exception e) {
            log.error("Error during in-progress verification check", e);
        }
    }
    
    /**
     * Check for expired verifications every hour
     */
    @Scheduled(fixedDelay = 3600000) // 1 hour
    @Transactional
    public void checkExpiredVerifications() {
        log.info("Starting expired verification check");
        
        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(30); // 30 days timeout
        
        try {
            List<KYCVerification> expiredVerifications = verificationRepository
                    .findByStatusAndCreatedAtBefore(
                            KYCVerification.Status.IN_PROGRESS,
                            expiryThreshold
                    );
            
            for (KYCVerification verification : expiredVerifications) {
                try {
                    verification.setStatus(KYCVerification.Status.EXPIRED);
                    verification.setNotes("Automatically expired due to timeout");
                    verificationRepository.save(verification);
                    
                    // Publish expired event
                    eventPublisher.publish(KYCVerificationExpiredEvent.builder()
                            .verificationId(verification.getId())
                            .userId(verification.getUserId())
                            .expiredAt(LocalDateTime.now())
                            .build());
                    
                    // Cancel with provider if possible
                    if (verification.getProviderReference() != null) {
                        try {
                            providerService.cancelVerificationSession(verification.getProviderReference());
                        } catch (Exception e) {
                            log.warn("Failed to cancel expired session with provider", e);
                        }
                    }
                    
                    log.info("Marked verification {} as expired", verification.getId());
                    
                } catch (Exception e) {
                    log.error("Failed to expire verification: {}", verification.getId(), e);
                }
            }
            
            log.info("Expired {} verifications", expiredVerifications.size());
            
        } catch (Exception e) {
            log.error("Error during expired verification check", e);
        }
    }
    
    /**
     * Send reminders for incomplete verifications every day
     */
    @Scheduled(cron = "0 0 10 * * *") // Daily at 10 AM
    @Transactional
    public void sendVerificationReminders() {
        log.info("Starting verification reminder process");
        
        LocalDateTime reminderThreshold = LocalDateTime.now().minusDays(3); // Remind after 3 days
        
        try {
            List<KYCVerification> pendingVerifications = verificationRepository
                    .findByStatusAndCreatedAtBeforeAndReminderSentFalse(
                            KYCVerification.Status.PENDING,
                            reminderThreshold
                    );
            
            for (KYCVerification verification : pendingVerifications) {
                try {
                    // Send reminder notification
                    notificationService.sendVerificationReminder(
                            verification.getUserId(),
                            verification.getId()
                    );
                    
                    // Mark reminder as sent
                    verification.setReminderSent(true);
                    verificationRepository.save(verification);
                    
                    // Publish reminder event
                    eventPublisher.publish(KYCVerificationReminderEvent.builder()
                            .verificationId(verification.getId())
                            .userId(verification.getUserId())
                            .reminderSentAt(LocalDateTime.now())
                            .build());
                    
                    log.info("Sent reminder for verification: {}", verification.getId());
                    
                } catch (Exception e) {
                    log.error("Failed to send reminder for verification: {}", verification.getId(), e);
                }
            }
            
            log.info("Sent {} verification reminders", pendingVerifications.size());
            
        } catch (Exception e) {
            log.error("Error during verification reminder process", e);
        }
    }
    
    /**
     * Clean up old completed verifications every week
     */
    @Scheduled(cron = "0 0 2 * * SUN") // Weekly on Sunday at 2 AM
    @Transactional
    public void cleanupOldVerifications() {
        log.info("Starting old verification cleanup");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(1); // Keep for 1 year
        
        try {
            List<KYCVerification> oldVerifications = verificationRepository
                    .findByStatusInAndCompletedAtBefore(
                            List.of(KYCVerification.Status.VERIFIED, KYCVerification.Status.REJECTED),
                            cutoffDate
                    );
            
            for (KYCVerification verification : oldVerifications) {
                try {
                    // Archive verification data
                    archiveVerification(verification);
                    
                    // Delete from active database
                    verificationRepository.delete(verification);
                    
                    log.info("Archived and deleted old verification: {}", verification.getId());
                    
                } catch (Exception e) {
                    log.error("Failed to cleanup verification: {}", verification.getId(), e);
                }
            }
            
            log.info("Cleaned up {} old verifications", oldVerifications.size());
            
        } catch (Exception e) {
            log.error("Error during old verification cleanup", e);
        }
    }
    
    /**
     * Monitor provider health every 15 minutes
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes
    public void monitorProviderHealth() {
        log.info("Starting provider health check");
        
        try {
            boolean isHealthy = providerService.isProviderAvailable();
            
            if (!isHealthy) {
                log.error("KYC provider is not healthy!");
                // Send alert to operations team
                notificationService.sendProviderHealthAlert(providerService.getProviderName(), false);
            } else {
                log.info("KYC provider {} is healthy", providerService.getProviderName());
            }
            
        } catch (Exception e) {
            log.error("Error during provider health check", e);
        }
    }
    
    /**
     * Generate daily KYC statistics report
     */
    @Scheduled(cron = "0 0 1 * * *") // Daily at 1 AM
    public void generateDailyStats() {
        log.info("Generating daily KYC statistics");
        
        try {
            LocalDateTime startOfDay = LocalDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            
            Map<String, Object> stats = verificationService.getVerificationStatistics(startOfDay, endOfDay);
            
            log.info("Daily KYC Stats: {}", stats);
            
            // Store or send the statistics as needed
            notificationService.sendDailyStatsReport(stats);
            
        } catch (Exception e) {
            log.error("Error generating daily statistics", e);
        }
    }
    
    // Helper methods
    
    private void checkVerificationStatus(KYCVerification verification) {
        try {
            if (verification.getProviderReference() == null) {
                return;
            }
            
            Map<String, Object> status = providerService.getVerificationStatus(
                    verification.getProviderReference()
            );
            
            String providerStatus = (String) status.get("status");
            
            // Update verification based on provider status
            switch (providerStatus != null ? providerStatus.toUpperCase() : "") {
                case "VERIFIED":
                case "APPROVED":
                case "CLEAR":
                    verificationService.updateVerificationStatus(
                            verification.getId(),
                            KYCVerification.Status.VERIFIED,
                            "Automatically verified by provider",
                            status
                    );
                    break;
                    
                case "REJECTED":
                case "DECLINED":
                case "FAILED":
                    verificationService.updateVerificationStatus(
                            verification.getId(),
                            KYCVerification.Status.REJECTED,
                            "Automatically rejected by provider",
                            status
                    );
                    break;
                    
                case "REVIEW":
                case "MANUAL_REVIEW":
                case "CONSIDER":
                    verificationService.updateVerificationStatus(
                            verification.getId(),
                            KYCVerification.Status.MANUAL_REVIEW,
                            "Requires manual review",
                            status
                    );
                    break;
                    
                case "EXPIRED":
                case "ABANDONED":
                    verificationService.updateVerificationStatus(
                            verification.getId(),
                            KYCVerification.Status.EXPIRED,
                            "Session expired at provider",
                            status
                    );
                    break;
            }
            
        } catch (Exception e) {
            log.error("Failed to check status for verification: {}", verification.getId(), e);
        }
    }
    
    private void archiveVerification(KYCVerification verification) {
        try {
            log.info("Archiving verification: {}", verification.getId());
            
            Map<String, Object> archiveData = Map.of(
                "verificationId", verification.getId(),
                "userId", verification.getUserId(),
                "status", verification.getStatus().name(),
                "verificationLevel", verification.getVerificationLevel(),
                "riskScore", verification.getRiskScore() != null ? verification.getRiskScore() : 0,
                "createdAt", verification.getCreatedAt().toString(),
                "completedAt", verification.getCompletedAt() != null ? verification.getCompletedAt().toString() : "",
                "archivedAt", LocalDateTime.now().toString(),
                "metadata", verification.getMetadata()
            );
            
            eventPublisher.publish("kyc.verification.archived", archiveData);
            
            verification.setArchived(true);
            verification.setArchivedAt(LocalDateTime.now());
            verificationRepository.save(verification);
            
            log.info("Successfully archived verification: {}", verification.getId());
            
        } catch (Exception e) {
            log.error("Failed to archive verification: {}", verification.getId(), e);
        }
    }
}