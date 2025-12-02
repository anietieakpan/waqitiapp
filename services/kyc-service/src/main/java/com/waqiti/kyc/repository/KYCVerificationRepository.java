package com.waqiti.kyc.repository;

import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.kyc.domain.KYCVerification.KYCStatus;
import com.waqiti.kyc.domain.KYCVerification.Status;
import com.waqiti.kyc.domain.KYCVerification.VerificationLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface KYCVerificationRepository extends JpaRepository<KYCVerification, String> {

    Optional<KYCVerification> findByUserIdAndIsActiveTrue(String userId);

    // Alias method for active verification lookup
    default Optional<KYCVerification> findActiveVerificationByUserId(String userId) {
        return findByUserIdAndIsActiveTrue(userId);
    }

    List<KYCVerification> findByUserId(String userId);

    List<KYCVerification> findByUserIdOrderByCreatedAtDesc(String userId);
    
    Optional<KYCVerification> findByProviderId(String providerId);
    
    Page<KYCVerification> findByStatus(KYCStatus status, Pageable pageable);
    
    @Query("SELECT k FROM KYCVerification k WHERE k.status = :status AND k.createdAt < :before")
    List<KYCVerification> findByStatusAndCreatedBefore(
        @Param("status") KYCStatus status, 
        @Param("before") LocalDateTime before
    );
    
    @Query("SELECT k FROM KYCVerification k WHERE k.expiresAt < :now AND k.status = :status")
    List<KYCVerification> findExpiredVerifications(@Param("now") LocalDateTime now, @Param("status") KYCStatus status);
    
    @Query("SELECT COUNT(k) FROM KYCVerification k WHERE k.status = :status")
    long countByStatus(@Param("status") KYCStatus status);
    
    @Query("SELECT k FROM KYCVerification k WHERE k.userId = :userId AND k.status = :status AND k.expiresAt > :now")
    Optional<KYCVerification> findActiveVerificationForUser(
        @Param("userId") String userId,
        @Param("now") LocalDateTime now,
        @Param("status") KYCStatus status
    );
    
    @Query("SELECT k FROM KYCVerification k WHERE k.verificationLevel = :level AND k.status = :status AND k.userId = :userId")
    Optional<KYCVerification> findByUserIdAndVerificationLevel(
        @Param("userId") String userId,
        @Param("level") VerificationLevel level,
        @Param("status") KYCStatus status
    );
    
    @Query("SELECT k FROM KYCVerification k WHERE k.status = :status AND " +
           "FUNCTION('JSON_EXTRACT', k.metadata, '$.flagged') = 'true'")
    List<KYCVerification> findFlaggedVerifications(@Param("status") KYCStatus status);
    
    boolean existsByUserIdAndStatusAndExpiresAtAfter(String userId, KYCStatus status, LocalDateTime expiresAt);
    
    // Methods for scheduler
    Page<KYCVerification> findByStatus(Status status, Pageable pageable);
    
    List<KYCVerification> findByStatusAndCreatedAtBefore(Status status, LocalDateTime before);
    
    @Query("SELECT k FROM KYCVerification k WHERE k.status = :status AND k.createdAt < :before AND k.reminderSent = false")
    List<KYCVerification> findByStatusAndCreatedAtBeforeAndReminderSentFalse(
        @Param("status") Status status, 
        @Param("before") LocalDateTime before
    );
    
    List<KYCVerification> findByStatusInAndCompletedAtBefore(List<Status> statuses, LocalDateTime before);
    
    Optional<KYCVerification> findByProviderReference(String providerReference);
    
    // Additional methods for statistics
    long countByStatus(Status status);
    
    long countByStatusAndCreatedAtBetween(Status status, LocalDateTime start, LocalDateTime end);
    
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    long countByCompletedAtBetween(LocalDateTime start, LocalDateTime end);
    
    long countByStatusAndCompletedAtBetween(Status status, LocalDateTime start, LocalDateTime end);
    
    List<KYCVerification> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    List<KYCVerification> findByStatusInAndCreatedAtBetween(List<Status> statuses, LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT v FROM KYCVerification v WHERE v.userId IN " +
           "(SELECT u.id FROM User u WHERE u.organizationId = :orgId) " +
           "AND v.createdAt BETWEEN :start AND :end")
    List<KYCVerification> findByOrganizationIdAndCreatedAtBetween(
            @Param("orgId") String organizationId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}