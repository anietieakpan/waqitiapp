package com.waqiti.kyc.migration;

import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.kyc.domain.VerificationDocument;
import com.waqiti.kyc.repository.KYCVerificationRepository;
import com.waqiti.kyc.repository.VerificationDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service to handle migration of KYC data from user service to dedicated KYC service
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "kyc.migration.enabled", havingValue = "true")
public class KYCMigrationService {

    private final JdbcTemplate jdbcTemplate;
    private final KYCVerificationRepository kycVerificationRepository;
    private final VerificationDocumentRepository documentRepository;

    @Transactional
    public MigrationResult migrateUserKYCData(String userId) {
        log.info("Starting KYC migration for user: {}", userId);
        
        MigrationResult result = new MigrationResult();
        result.setUserId(userId);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // Step 1: Check if user already has KYC data in new service
            if (kycVerificationRepository.existsByUserId(userId)) {
                log.warn("User {} already has KYC data in new service, skipping migration", userId);
                result.setStatus(MigrationStatus.SKIPPED);
                result.setMessage("KYC data already exists in new service");
                return result;
            }
            
            // Step 2: Extract KYC data from user service database
            LegacyKYCData legacyData = extractLegacyKYCData(userId);
            
            if (legacyData == null || legacyData.isEmpty()) {
                log.info("No legacy KYC data found for user: {}", userId);
                result.setStatus(MigrationStatus.NO_DATA);
                result.setMessage("No KYC data to migrate");
                return result;
            }
            
            // Step 3: Transform and migrate the data
            migrateVerifications(userId, legacyData, result);
            migrateDocuments(userId, legacyData, result);
            
            // Step 4: Mark migration as complete
            markMigrationComplete(userId);
            
            result.setStatus(MigrationStatus.SUCCESS);
            result.setMessage("Migration completed successfully");
            result.setEndTime(LocalDateTime.now());
            
            log.info("Successfully migrated KYC data for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to migrate KYC data for user: {}", userId, e);
            result.setStatus(MigrationStatus.FAILED);
            result.setMessage("Migration failed: " + e.getMessage());
            result.setError(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }

    @Transactional
    public BatchMigrationResult migrateBatchUsers(List<String> userIds) {
        log.info("Starting batch migration for {} users", userIds.size());
        
        BatchMigrationResult batchResult = new BatchMigrationResult();
        batchResult.setTotalUsers(userIds.size());
        batchResult.setStartTime(LocalDateTime.now());
        
        for (String userId : userIds) {
            try {
                MigrationResult result = migrateUserKYCData(userId);
                batchResult.addResult(result);
                
                // Add delay between migrations to avoid overwhelming the system
                Thread.sleep(100);
                
            } catch (Exception e) {
                log.error("Failed to migrate user {}: {}", userId, e.getMessage());
                MigrationResult failedResult = new MigrationResult();
                failedResult.setUserId(userId);
                failedResult.setStatus(MigrationStatus.FAILED);
                failedResult.setError(e.getMessage());
                batchResult.addResult(failedResult);
            }
        }
        
        batchResult.setEndTime(LocalDateTime.now());
        log.info("Batch migration completed: {} successful, {} failed, {} skipped", 
                batchResult.getSuccessCount(), 
                batchResult.getFailedCount(), 
                batchResult.getSkippedCount());
        
        return batchResult;
    }

    public boolean isMigrationRequired(String userId) {
        // Check if user has KYC data in legacy system but not in new system
        boolean hasLegacyData = hasLegacyKYCData(userId);
        boolean hasNewData = kycVerificationRepository.existsByUserId(userId);
        
        return hasLegacyData && !hasNewData;
    }

    public MigrationStats getMigrationStatistics() {
        String sql = """
            SELECT 
                COUNT(CASE WHEN migrated = true THEN 1 END) as migrated_count,
                COUNT(CASE WHEN migrated = false THEN 1 END) as pending_count,
                COUNT(*) as total_count
            FROM user_kyc_migration_status
        """;
        
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            MigrationStats stats = new MigrationStats();
            stats.setMigratedCount(rs.getInt("migrated_count"));
            stats.setPendingCount(rs.getInt("pending_count"));
            stats.setTotalCount(rs.getInt("total_count"));
            return stats;
        });
    }

    private LegacyKYCData extractLegacyKYCData(String userId) {
        // Extract KYC data from legacy user service tables
        String kycSql = """
            SELECT 
                u.id as user_id,
                u.kyc_status,
                u.kyc_level,
                u.kyc_verified_at,
                u.kyc_provider,
                u.kyc_session_id,
                u.kyc_attempt_count,
                u.kyc_rejection_reason,
                u.created_at,
                u.updated_at
            FROM users u 
            WHERE u.id = ? AND u.kyc_status IS NOT NULL
        """;
        
        List<Map<String, Object>> kycResults = jdbcTemplate.queryForList(kycSql, userId);
        
        if (kycResults.isEmpty()) {
            log.info("No legacy KYC data found for user: {}", userId);
            
            // Return empty/default KYC data instead of null
            return LegacyKYCData.builder()
                .userId(userId)
                .status(KYCStatus.NOT_STARTED)
                .level(KYCLevel.NONE)
                .documentsVerified(false)
                .riskRating("UNKNOWN")
                .migrationStatus("NO_LEGACY_DATA")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        }
        
        Map<String, Object> kycRow = kycResults.get(0);
        LegacyKYCData legacyData = new LegacyKYCData();
        
        // Map legacy data to new structure
        legacyData.setUserId(userId);
        legacyData.setStatus(mapLegacyStatus((String) kycRow.get("kyc_status")));
        legacyData.setLevel(mapLegacyLevel((String) kycRow.get("kyc_level")));
        legacyData.setVerifiedAt((LocalDateTime) kycRow.get("kyc_verified_at"));
        legacyData.setProvider((String) kycRow.get("kyc_provider"));
        legacyData.setSessionId((String) kycRow.get("kyc_session_id"));
        legacyData.setAttemptCount((Integer) kycRow.get("kyc_attempt_count"));
        legacyData.setRejectionReason((String) kycRow.get("kyc_rejection_reason"));
        legacyData.setCreatedAt((LocalDateTime) kycRow.get("created_at"));
        legacyData.setUpdatedAt((LocalDateTime) kycRow.get("updated_at"));
        
        // Extract document data
        extractLegacyDocuments(userId, legacyData);
        
        return legacyData;
    }

    private void extractLegacyDocuments(String userId, LegacyKYCData legacyData) {
        String docSql = """
            SELECT 
                id,
                document_type,
                file_name,
                file_path,
                file_size,
                content_type,
                status,
                uploaded_at,
                verified_at,
                rejection_reason,
                metadata,
                created_at,
                updated_at
            FROM user_documents 
            WHERE user_id = ? AND document_category = 'KYC'
        """;
        
        List<Map<String, Object>> documents = jdbcTemplate.queryForList(docSql, userId);
        legacyData.setDocuments(documents);
    }

    private void migrateVerifications(String userId, LegacyKYCData legacyData, MigrationResult result) {
        KYCVerification verification = new KYCVerification();
        verification.setId(UUID.randomUUID().toString());
        verification.setUserId(userId);
        verification.setStatus(legacyData.getStatus());
        verification.setVerificationLevel(legacyData.getLevel());
        verification.setProvider(legacyData.getProvider());
        verification.setProviderSessionId(legacyData.getSessionId());
        verification.setAttemptCount(legacyData.getAttemptCount());
        verification.setVerifiedAt(legacyData.getVerifiedAt());
        verification.setRejectionReason(legacyData.getRejectionReason());
        verification.setCreatedAt(legacyData.getCreatedAt());
        verification.setUpdatedAt(legacyData.getUpdatedAt());
        
        // Set expiry date if verified
        if (verification.getVerifiedAt() != null) {
            verification.setExpiresAt(verification.getVerifiedAt().plusDays(365));
        }
        
        kycVerificationRepository.save(verification);
        result.setMigratedVerifications(1);
        
        log.debug("Migrated verification for user: {}", userId);
    }

    private void migrateDocuments(String userId, LegacyKYCData legacyData, MigrationResult result) {
        if (legacyData.getDocuments() == null || legacyData.getDocuments().isEmpty()) {
            return;
        }
        
        // Get the verification ID for the user
        KYCVerification verification = kycVerificationRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Verification not found for user: " + userId));
        
        int documentCount = 0;
        for (Map<String, Object> docData : legacyData.getDocuments()) {
            VerificationDocument document = new VerificationDocument();
            document.setId(UUID.randomUUID().toString());
            document.setVerification(verification);
            document.setDocumentType(mapLegacyDocumentType((String) docData.get("document_type")));
            document.setStatus(mapLegacyDocumentStatus((String) docData.get("status")));
            document.setFileName((String) docData.get("file_name"));
            document.setFilePath((String) docData.get("file_path"));
            document.setFileSize((Long) docData.get("file_size"));
            document.setContentType((String) docData.get("content_type"));
            document.setUploadedAt((LocalDateTime) docData.get("uploaded_at"));
            document.setVerifiedAt((LocalDateTime) docData.get("verified_at"));
            document.setRejectionReason((String) docData.get("rejection_reason"));
            document.setCreatedAt((LocalDateTime) docData.get("created_at"));
            document.setUpdatedAt((LocalDateTime) docData.get("updated_at"));
            
            documentRepository.save(document);
            documentCount++;
        }
        
        result.setMigratedDocuments(documentCount);
        log.debug("Migrated {} documents for user: {}", documentCount, userId);
    }

    private void markMigrationComplete(String userId) {
        String sql = """
            INSERT INTO user_kyc_migration_status (user_id, migrated, migrated_at) 
            VALUES (?, true, ?)
            ON CONFLICT (user_id) DO UPDATE SET 
                migrated = true, 
                migrated_at = ?
        """;
        
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql, userId, now, now);
    }

    private boolean hasLegacyKYCData(String userId) {
        String sql = "SELECT COUNT(*) FROM users WHERE id = ? AND kyc_status IS NOT NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count != null && count > 0;
    }

    // Mapping methods for legacy to new enum conversion
    private KYCVerification.KYCStatus mapLegacyStatus(String legacyStatus) {
        if (legacyStatus == null) return KYCVerification.KYCStatus.NOT_STARTED;
        
        return switch (legacyStatus.toLowerCase()) {
            case "pending" -> KYCVerification.KYCStatus.PENDING;
            case "in_progress" -> KYCVerification.KYCStatus.IN_PROGRESS;
            case "approved", "verified" -> KYCVerification.KYCStatus.APPROVED;
            case "rejected", "failed" -> KYCVerification.KYCStatus.REJECTED;
            case "expired" -> KYCVerification.KYCStatus.EXPIRED;
            case "cancelled" -> KYCVerification.KYCStatus.CANCELLED;
            default -> KYCVerification.KYCStatus.PENDING;
        };
    }

    private KYCVerification.VerificationLevel mapLegacyLevel(String legacyLevel) {
        if (legacyLevel == null) return KYCVerification.VerificationLevel.BASIC;
        
        return switch (legacyLevel.toLowerCase()) {
            case "basic", "level1" -> KYCVerification.VerificationLevel.BASIC;
            case "intermediate", "level2" -> KYCVerification.VerificationLevel.INTERMEDIATE;
            case "advanced", "level3" -> KYCVerification.VerificationLevel.ADVANCED;
            default -> KYCVerification.VerificationLevel.BASIC;
        };
    }

    private VerificationDocument.DocumentType mapLegacyDocumentType(String legacyType) {
        if (legacyType == null) return VerificationDocument.DocumentType.OTHER;
        
        return switch (legacyType.toLowerCase()) {
            case "passport" -> VerificationDocument.DocumentType.PASSPORT;
            case "drivers_license", "driving_license" -> VerificationDocument.DocumentType.DRIVERS_LICENSE;
            case "national_id", "id_card" -> VerificationDocument.DocumentType.NATIONAL_ID;
            case "proof_of_address" -> VerificationDocument.DocumentType.PROOF_OF_ADDRESS;
            case "bank_statement" -> VerificationDocument.DocumentType.BANK_STATEMENT;
            case "utility_bill" -> VerificationDocument.DocumentType.UTILITY_BILL;
            case "selfie" -> VerificationDocument.DocumentType.SELFIE;
            case "selfie_with_document" -> VerificationDocument.DocumentType.SELFIE_WITH_DOCUMENT;
            default -> VerificationDocument.DocumentType.OTHER;
        };
    }

    private VerificationDocument.DocumentStatus mapLegacyDocumentStatus(String legacyStatus) {
        if (legacyStatus == null) return VerificationDocument.DocumentStatus.PENDING;
        
        return switch (legacyStatus.toLowerCase()) {
            case "pending" -> VerificationDocument.DocumentStatus.PENDING;
            case "processing" -> VerificationDocument.DocumentStatus.PROCESSING;
            case "verified", "approved" -> VerificationDocument.DocumentStatus.VERIFIED;
            case "rejected", "failed" -> VerificationDocument.DocumentStatus.REJECTED;
            case "expired" -> VerificationDocument.DocumentStatus.EXPIRED;
            default -> VerificationDocument.DocumentStatus.PENDING;
        };
    }
}