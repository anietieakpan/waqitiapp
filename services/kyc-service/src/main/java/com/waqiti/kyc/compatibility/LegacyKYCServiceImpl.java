package com.waqiti.kyc.compatibility;

import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCStatusResponse;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of legacy KYC service operations
 * This service interacts with the legacy user service database
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "kyc.compatibility.enabled", havingValue = "true", matchIfMissing = true)
public class LegacyKYCServiceImpl implements LegacyKYCService {

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;

    @Override
    @Transactional
    public KYCVerificationResponse initiateVerification(String userId, KYCVerificationRequest request) {
        log.debug("Initiating legacy KYC verification for user: {}", userId);
        
        // Update user table with KYC status
        String updateSql = """
            UPDATE users 
            SET kyc_status = ?, 
                kyc_level = ?, 
                kyc_provider = ?,
                kyc_session_id = ?,
                kyc_attempt_count = COALESCE(kyc_attempt_count, 0) + 1,
                updated_at = ?
            WHERE id = ?
        """;
        
        String sessionId = UUID.randomUUID().toString();
        jdbcTemplate.update(updateSql, 
            "PENDING",
            request.getVerificationLevel().name(),
            request.getPreferredProvider() != null ? request.getPreferredProvider() : "ONFIDO",
            sessionId,
            LocalDateTime.now(),
            userId
        );
        
        // Insert into legacy KYC tracking table if exists
        try {
            String insertTrackingSql = """
                INSERT INTO user_kyc_tracking (id, user_id, status, level, provider, session_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            
            String trackingId = UUID.randomUUID().toString();
            jdbcTemplate.update(insertTrackingSql,
                trackingId,
                userId,
                "PENDING",
                request.getVerificationLevel().name(),
                request.getPreferredProvider(),
                sessionId,
                LocalDateTime.now()
            );
        } catch (Exception e) {
            log.warn("Could not insert into legacy tracking table: {}", e.getMessage());
        }
        
        // Build response
        return KYCVerificationResponse.builder()
                .id(sessionId)
                .userId(userId)
                .status(KYCVerificationResponse.KYCStatus.PENDING)
                .verificationLevel(KYCVerificationResponse.VerificationLevel.valueOf(
                    request.getVerificationLevel().name()))
                .provider(request.getPreferredProvider())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    public KYCStatusResponse getUserKYCStatus(String userId) {
        log.debug("Getting legacy KYC status for user: {}", userId);
        
        String sql = """
            SELECT kyc_status, kyc_level, kyc_verified_at, kyc_provider, kyc_attempt_count
            FROM users 
            WHERE id = ?
        """;
        
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            String status = rs.getString("kyc_status");
            String level = rs.getString("kyc_level");
            LocalDateTime verifiedAt = rs.getTimestamp("kyc_verified_at") != null ? 
                rs.getTimestamp("kyc_verified_at").toLocalDateTime() : null;
            
            KYCVerificationResponse.KYCStatus kycStatus = status != null ? 
                mapLegacyStatus(status) : KYCVerificationResponse.KYCStatus.NOT_STARTED;
                
            KYCVerificationResponse.VerificationLevel verificationLevel = level != null ?
                KYCVerificationResponse.VerificationLevel.valueOf(level) : 
                KYCVerificationResponse.VerificationLevel.BASIC;
            
            return KYCStatusResponse.builder()
                    .userId(userId)
                    .currentStatus(kycStatus)
                    .currentLevel(verificationLevel)
                    .lastVerifiedAt(verifiedAt)
                    .isActive(kycStatus == KYCVerificationResponse.KYCStatus.APPROVED)
                    .canUpgrade(verificationLevel != KYCVerificationResponse.VerificationLevel.ADVANCED)
                    .build();
        }, userId);
    }

    @Override
    public boolean isUserVerified(String userId, String level) {
        log.debug("Checking legacy verification status for user: {} at level: {}", userId, level);
        
        String sql = """
            SELECT COUNT(*) 
            FROM users 
            WHERE id = ? 
            AND kyc_status = 'APPROVED' 
            AND kyc_level >= ?
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, level);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void syncFromNewService(String userId, KYCVerificationResponse newResponse) {
        log.debug("Syncing legacy system from new service for user: {}", userId);
        
        String updateSql = """
            UPDATE users 
            SET kyc_status = ?, 
                kyc_level = ?, 
                kyc_verified_at = ?,
                kyc_provider = ?,
                kyc_session_id = ?,
                kyc_rejection_reason = ?,
                updated_at = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(updateSql,
            mapNewStatusToLegacy(newResponse.getStatus()),
            newResponse.getVerificationLevel().name(),
            newResponse.getVerifiedAt(),
            newResponse.getProvider(),
            newResponse.getId(),
            newResponse.getRejectionReason(),
            LocalDateTime.now(),
            userId
        );
    }

    @Override
    public boolean isHealthy() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.error("Legacy KYC service health check failed: {}", e.getMessage());
            return false;
        }
    }

    private KYCVerificationResponse.KYCStatus mapLegacyStatus(String legacyStatus) {
        return switch (legacyStatus.toUpperCase()) {
            case "PENDING" -> KYCVerificationResponse.KYCStatus.PENDING;
            case "IN_PROGRESS" -> KYCVerificationResponse.KYCStatus.IN_PROGRESS;
            case "APPROVED", "VERIFIED" -> KYCVerificationResponse.KYCStatus.APPROVED;
            case "REJECTED", "FAILED" -> KYCVerificationResponse.KYCStatus.REJECTED;
            case "EXPIRED" -> KYCVerificationResponse.KYCStatus.EXPIRED;
            case "CANCELLED" -> KYCVerificationResponse.KYCStatus.CANCELLED;
            default -> KYCVerificationResponse.KYCStatus.PENDING;
        };
    }

    private String mapNewStatusToLegacy(KYCVerificationResponse.KYCStatus status) {
        return switch (status) {
            case NOT_STARTED -> "NOT_STARTED";
            case PENDING -> "PENDING";
            case IN_PROGRESS -> "IN_PROGRESS";
            case PENDING_REVIEW -> "PENDING_REVIEW";
            case REQUIRES_ADDITIONAL_INFO -> "REQUIRES_INFO";
            case APPROVED -> "APPROVED";
            case REJECTED -> "REJECTED";
            case EXPIRED -> "EXPIRED";
            case CANCELLED -> "CANCELLED";
        };
    }
}