package com.waqiti.legal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legal Signature Repository
 *
 * Complete data access layer for LegalSignature entities with custom query methods
 * Supports digital signatures, e-signatures, and signature verification tracking
 *
 * Note: This repository is designed for a LegalSignature entity that should be created
 * to track signatures on legal documents and contracts
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface LegalSignatureRepository extends JpaRepository<LegalSignature, UUID> {

    /**
     * Find signature by signature ID
     */
    Optional<LegalSignature> findBySignatureId(String signatureId);

    /**
     * Find all signatures for a document
     */
    List<LegalSignature> findByDocumentId(String documentId);

    /**
     * Find all signatures for a contract
     */
    List<LegalSignature> findByContractId(String contractId);

    /**
     * Find signatures by signer ID
     */
    List<LegalSignature> findBySignerId(String signerId);

    /**
     * Find signatures by signer email
     */
    List<LegalSignature> findBySignerEmail(String signerEmail);

    /**
     * Find signatures by signature type
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.signatureType = :signatureType")
    List<LegalSignature> findBySignatureType(@Param("signatureType") String signatureType);

    /**
     * Find verified signatures
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.verified = true")
    List<LegalSignature> findVerifiedSignatures();

    /**
     * Find unverified signatures
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.verified = false OR s.verified IS NULL")
    List<LegalSignature> findUnverifiedSignatures();

    /**
     * Find pending signatures (not yet signed)
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.signedAt IS NULL AND s.status = 'PENDING'")
    List<LegalSignature> findPendingSignatures();

    /**
     * Find completed signatures
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.signedAt IS NOT NULL AND s.status = 'COMPLETED'")
    List<LegalSignature> findCompletedSignatures();

    /**
     * Find signatures by verification method
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.verificationMethod = :method")
    List<LegalSignature> findByVerificationMethod(@Param("method") String method);

    /**
     * Find signatures requiring notarization
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.requiresNotarization = true " +
           "AND (s.notarized IS NULL OR s.notarized = false)")
    List<LegalSignature> findRequiringNotarization();

    /**
     * Find notarized signatures
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.notarized = true")
    List<LegalSignature> findNotarizedSignatures();

    /**
     * Find signatures by IP address
     */
    List<LegalSignature> findByIpAddress(String ipAddress);

    /**
     * Find signatures within date range
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.signedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY s.signedAt DESC")
    List<LegalSignature> findBySignedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find signatures by document and check if all parties signed
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.documentId = :documentId " +
           "AND s.signedAt IS NOT NULL")
    List<LegalSignature> findCompletedSignaturesByDocument(@Param("documentId") String documentId);

    /**
     * Find pending signatures by document
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.documentId = :documentId " +
           "AND s.signedAt IS NULL")
    List<LegalSignature> findPendingSignaturesByDocument(@Param("documentId") String documentId);

    /**
     * Find overdue signatures (invitation sent but not completed within deadline)
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.signedAt IS NULL " +
           "AND s.deadline IS NOT NULL " +
           "AND s.deadline < :currentDateTime")
    List<LegalSignature> findOverdueSignatures(@Param("currentDateTime") LocalDateTime currentDateTime);

    /**
     * Find signatures approaching deadline
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.signedAt IS NULL " +
           "AND s.deadline BETWEEN :startDateTime AND :endDateTime")
    List<LegalSignature> findSignaturesApproachingDeadline(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Find signatures by certificate ID (for digital signatures)
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.certificateId = :certificateId")
    List<LegalSignature> findByCertificateId(@Param("certificateId") String certificateId);

    /**
     * Find signatures with failed verification
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.verificationStatus = 'FAILED'")
    List<LegalSignature> findFailedVerifications();

    /**
     * Find signatures by witness
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.witnessId = :witnessId")
    List<LegalSignature> findByWitnessId(@Param("witnessId") String witnessId);

    /**
     * Count signatures by document
     */
    @Query("SELECT COUNT(s) FROM LegalSignature s WHERE s.documentId = :documentId")
    long countSignaturesByDocument(@Param("documentId") String documentId);

    /**
     * Count completed signatures by document
     */
    @Query("SELECT COUNT(s) FROM LegalSignature s WHERE s.documentId = :documentId " +
           "AND s.signedAt IS NOT NULL")
    long countCompletedSignaturesByDocument(@Param("documentId") String documentId);

    /**
     * Count pending signatures by signer
     */
    @Query("SELECT COUNT(s) FROM LegalSignature s WHERE s.signerId = :signerId " +
           "AND s.signedAt IS NULL")
    long countPendingSignaturesBySigner(@Param("signerId") String signerId);

    /**
     * Check if signature exists for document and signer
     */
    boolean existsByDocumentIdAndSignerId(String documentId, String signerId);

    /**
     * Check if document is fully signed
     */
    @Query("SELECT CASE WHEN COUNT(s) = 0 THEN true ELSE false END FROM LegalSignature s " +
           "WHERE s.documentId = :documentId " +
           "AND s.signedAt IS NULL")
    boolean isDocumentFullySigned(@Param("documentId") String documentId);

    /**
     * Find signatures requiring reminder
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.signedAt IS NULL " +
           "AND s.lastReminderSent IS NOT NULL " +
           "AND s.lastReminderSent < :reminderThreshold " +
           "AND s.deadline > :currentDateTime")
    List<LegalSignature> findSignaturesRequiringReminder(
        @Param("reminderThreshold") LocalDateTime reminderThreshold,
        @Param("currentDateTime") LocalDateTime currentDateTime
    );

    /**
     * Find signatures by signature provider (e.g., DocuSign, Adobe Sign)
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.signatureProvider = :provider")
    List<LegalSignature> findBySignatureProvider(@Param("provider") String provider);

    /**
     * Find signatures by role (e.g., SIGNER, APPROVER, WITNESS)
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.signerRole = :role")
    List<LegalSignature> findBySignerRole(@Param("role") String role);

    /**
     * Find signatures with audit trail
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.auditTrailAvailable = true")
    List<LegalSignature> findSignaturesWithAuditTrail();

    /**
     * Search signatures by signer name
     */
    @Query("SELECT s FROM LegalSignature s WHERE LOWER(s.signerName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalSignature> searchBySignerName(@Param("searchTerm") String searchTerm);

    /**
     * Find revoked signatures
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.revoked = true")
    List<LegalSignature> findRevokedSignatures();

    /**
     * Find signatures by delegation (signed by delegate)
     */
    @Query("SELECT s FROM LegalSignature s WHERE s.delegatedBy IS NOT NULL")
    List<LegalSignature> findDelegatedSignatures();
}

/**
 * Placeholder class for LegalSignature entity
 * This should be created as a proper domain entity in com.waqiti.legal.domain package
 */
class LegalSignature {
    private UUID id;
    private String signatureId;
    private String documentId;
    private String contractId;
    private String signerId;
    private String signerName;
    private String signerEmail;
    private String signerRole;
    private String signatureType;
    private String signatureProvider;
    private Boolean verified;
    private String verificationMethod;
    private String verificationStatus;
    private Boolean requiresNotarization;
    private Boolean notarized;
    private String ipAddress;
    private LocalDateTime signedAt;
    private LocalDateTime deadline;
    private String certificateId;
    private String witnessId;
    private LocalDateTime lastReminderSent;
    private Boolean auditTrailAvailable;
    private Boolean revoked;
    private String delegatedBy;
    private String status;
}
