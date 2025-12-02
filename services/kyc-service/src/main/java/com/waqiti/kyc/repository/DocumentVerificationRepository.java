package com.waqiti.kyc.repository;

import com.waqiti.kyc.domain.DocumentVerification;
import com.waqiti.kyc.service.KYCDocumentVerificationService.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVerificationRepository extends JpaRepository<DocumentVerification, UUID> {

    Optional<DocumentVerification> findByIdAndUserId(UUID id, UUID userId);
    
    List<DocumentVerification> findByUserId(UUID userId);
    
    Page<DocumentVerification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    List<DocumentVerification> findByUserIdAndDocumentType(UUID userId, DocumentType documentType);
    
    Optional<DocumentVerification> findByUserIdAndDocumentTypeAndStatus(
        UUID userId, DocumentType documentType, DocumentVerification.Status status);
    
    List<DocumentVerification> findByUserIdAndStatusIn(UUID userId, List<DocumentVerification.Status> statuses);
    
    @Query("SELECT dv FROM DocumentVerification dv WHERE dv.status = :status AND dv.createdAt < :beforeDate")
    List<DocumentVerification> findStaleVerifications(
        @Param("status") DocumentVerification.Status status, 
        @Param("beforeDate") LocalDateTime beforeDate);
    
    @Query("SELECT dv FROM DocumentVerification dv WHERE dv.status = 'PENDING_REVIEW' " +
           "ORDER BY dv.createdAt ASC")
    Page<DocumentVerification> findPendingReview(Pageable pageable);
    
    @Query("SELECT COUNT(dv) FROM DocumentVerification dv WHERE dv.userId = :userId " +
           "AND dv.createdAt > :afterDate")
    int countByUserIdAndCreatedAtAfter(
        @Param("userId") UUID userId, 
        @Param("afterDate") LocalDateTime afterDate);
    
    @Query("SELECT COUNT(dv) FROM DocumentVerification dv WHERE dv.userId = :userId " +
           "AND dv.documentType = :documentType AND dv.status = 'VERIFIED' " +
           "AND dv.completedAt > :afterDate")
    int countVerifiedDocuments(
        @Param("userId") UUID userId,
        @Param("documentType") DocumentType documentType,
        @Param("afterDate") LocalDateTime afterDate);
    
    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, dv.createdAt, dv.completedAt)) " +
           "FROM DocumentVerification dv WHERE dv.status IN ('VERIFIED', 'REJECTED') " +
           "AND dv.completedAt IS NOT NULL AND dv.createdAt > :afterDate")
    Double calculateAverageProcessingTime(@Param("afterDate") LocalDateTime afterDate);
    
    @Query("SELECT dv.documentType, COUNT(dv), " +
           "SUM(CASE WHEN dv.status = 'VERIFIED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN dv.status = 'REJECTED' THEN 1 ELSE 0 END) " +
           "FROM DocumentVerification dv WHERE dv.createdAt > :afterDate " +
           "GROUP BY dv.documentType")
    List<Object[]> getVerificationStatsByType(@Param("afterDate") LocalDateTime afterDate);
    
    @Modifying
    @Query("UPDATE DocumentVerification dv SET dv.status = 'EXPIRED' " +
           "WHERE dv.status IN ('PENDING', 'PROCESSING') AND dv.expiresAt < :now")
    int expireOldVerifications(@Param("now") LocalDateTime now);
    
    @Query("SELECT dv FROM DocumentVerification dv WHERE dv.status = 'PROCESSING' " +
           "AND dv.createdAt < :beforeDate")
    List<DocumentVerification> findStuckProcessingVerifications(
        @Param("beforeDate") LocalDateTime beforeDate);
    
    boolean existsByUserIdAndDocumentTypeAndStatusAndCompletedAtAfter(
        UUID userId, DocumentType documentType, DocumentVerification.Status status, 
        LocalDateTime afterDate);
    
    @Query("SELECT dv.userId, COUNT(dv) FROM DocumentVerification dv " +
           "WHERE dv.status = 'REJECTED' AND dv.fraudScore > :fraudThreshold " +
           "AND dv.completedAt > :afterDate GROUP BY dv.userId HAVING COUNT(dv) > :minCount")
    List<Object[]> findSuspiciousUsers(
        @Param("fraudThreshold") Double fraudThreshold,
        @Param("afterDate") LocalDateTime afterDate,
        @Param("minCount") Long minCount);
    
    void deleteByUserIdAndCreatedAtBefore(UUID userId, LocalDateTime beforeDate);
}