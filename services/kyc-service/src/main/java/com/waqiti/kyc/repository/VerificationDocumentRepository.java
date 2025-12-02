package com.waqiti.kyc.repository;

import com.waqiti.kyc.domain.VerificationDocument;
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
public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, UUID> {

    List<VerificationDocument> findByUserId(UUID userId);
    
    List<VerificationDocument> findByUserIdAndDocumentType(UUID userId, DocumentType documentType);
    
    Optional<VerificationDocument> findByDocumentKey(String documentKey);
    
    Page<VerificationDocument> findByUserIdOrderByUploadedAtDesc(UUID userId, Pageable pageable);
    
    @Query("SELECT vd FROM VerificationDocument vd WHERE vd.userId = :userId " +
           "AND vd.status = 'ACTIVE' AND vd.expiresAt > :now")
    List<VerificationDocument> findActiveDocuments(
        @Param("userId") UUID userId, 
        @Param("now") LocalDateTime now);
    
    @Query("SELECT vd FROM VerificationDocument vd WHERE vd.status = 'PENDING_DELETION' " +
           "AND vd.scheduledDeletionAt < :now")
    List<VerificationDocument> findDocumentsForDeletion(@Param("now") LocalDateTime now);
    
    @Query("SELECT SUM(vd.fileSize) FROM VerificationDocument vd WHERE vd.userId = :userId")
    Long calculateTotalStorageUsed(@Param("userId") UUID userId);
    
    @Query("SELECT vd.documentType, COUNT(vd), SUM(vd.fileSize) " +
           "FROM VerificationDocument vd WHERE vd.userId = :userId " +
           "GROUP BY vd.documentType")
    List<Object[]> getDocumentStatsByUser(@Param("userId") UUID userId);
    
    @Modifying
    @Query("UPDATE VerificationDocument vd SET vd.status = 'ARCHIVED', " +
           "vd.archivedAt = :now WHERE vd.status = 'ACTIVE' " +
           "AND vd.expiresAt < :now")
    int archiveExpiredDocuments(@Param("now") LocalDateTime now);
    
    @Query("SELECT vd FROM VerificationDocument vd WHERE vd.status = 'ACTIVE' " +
           "AND vd.lastAccessedAt < :beforeDate ORDER BY vd.fileSize DESC")
    Page<VerificationDocument> findInactiveDocuments(
        @Param("beforeDate") LocalDateTime beforeDate, 
        Pageable pageable);
    
    boolean existsByUserIdAndDocumentTypeAndStatus(
        UUID userId, DocumentType documentType, VerificationDocument.Status status);
    
    @Query("SELECT COUNT(DISTINCT vd.userId) FROM VerificationDocument vd " +
           "WHERE vd.uploadedAt > :afterDate")
    Long countUniqueUsersWithDocuments(@Param("afterDate") LocalDateTime afterDate);
    
    @Modifying
    @Query("UPDATE VerificationDocument vd SET vd.lastAccessedAt = :now " +
           "WHERE vd.id = :documentId")
    void updateLastAccessed(
        @Param("documentId") UUID documentId, 
        @Param("now") LocalDateTime now);
    
    void deleteByUserIdAndStatusAndUploadedAtBefore(
        UUID userId, VerificationDocument.Status status, LocalDateTime beforeDate);
}