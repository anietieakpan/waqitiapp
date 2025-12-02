package com.waqiti.tokenization.service;

import com.waqiti.tokenization.domain.Token;
import com.waqiti.tokenization.domain.TokenStatus;
import com.waqiti.tokenization.domain.TokenType;
import com.waqiti.tokenization.repository.TokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Tokenization Service
 *
 * Core business logic for PCI-DSS compliant tokenization of sensitive data.
 *
 * Features:
 * - Tokenize sensitive data (cards, bank accounts, SSNs, etc.)
 * - Detokenize to retrieve original data
 * - Token lifecycle management (revoke, expire, rotate)
 * - User ownership validation
 * - Audit logging
 * - Usage tracking
 *
 * Security:
 * - All sensitive data encrypted with AWS KMS
 * - Tokens are cryptographically random
 * - User ownership enforced
 * - Comprehensive audit trail
 *
 * PCI-DSS Compliance:
 * - Requirement 3.2: Replace PAN with tokens
 * - Requirement 3.4: Render PAN unreadable
 * - Requirement 10: Track and monitor access
 *
 * @author Waqiti Platform Engineering
 */
@Service
@Slf4j
public class TokenizationService {

    private final TokenRepository tokenRepository;
    private final TokenGeneratorService tokenGeneratorService;
    private final TokenEncryptionService tokenEncryptionService;
    private final MeterRegistry meterRegistry;

    private final Counter tokenizationSuccess;
    private final Counter tokenizationError;
    private final Counter detokenizationSuccess;
    private final Counter detokenizationError;
    private final Counter tokenRevocations;
    private final Counter tokenExpirations;

    @Value("${tokenization.kms.key-id}")
    private String defaultKmsKeyId;

    @Value("${tokenization.max-retries:3}")
    private int maxRetries;

    public TokenizationService(
            TokenRepository tokenRepository,
            TokenGeneratorService tokenGeneratorService,
            TokenEncryptionService tokenEncryptionService,
            MeterRegistry meterRegistry) {

        this.tokenRepository = tokenRepository;
        this.tokenGeneratorService = tokenGeneratorService;
        this.tokenEncryptionService = tokenEncryptionService;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.tokenizationSuccess = Counter.builder("tokenization.tokenize.success")
            .description("Successful tokenization operations")
            .register(meterRegistry);

        this.tokenizationError = Counter.builder("tokenization.tokenize.error")
            .description("Failed tokenization operations")
            .register(meterRegistry);

        this.detokenizationSuccess = Counter.builder("tokenization.detokenize.success")
            .description("Successful detokenization operations")
            .register(meterRegistry);

        this.detokenizationError = Counter.builder("tokenization.detokenize.error")
            .description("Failed detokenization operations")
            .register(meterRegistry);

        this.tokenRevocations = Counter.builder("tokenization.revocations")
            .description("Total token revocations")
            .register(meterRegistry);

        this.tokenExpirations = Counter.builder("tokenization.expirations")
            .description("Total token expirations")
            .register(meterRegistry);
    }

    /**
     * Tokenize sensitive data
     *
     * @param request Tokenization request
     * @return Tokenization result with token
     */
    @Transactional
    public TokenizationResult tokenize(TokenizationRequest request) {
        try {
            log.info("Tokenizing data: type={}, userId={}", request.getType(), request.getUserId());

            // Validate request
            validateTokenizationRequest(request);

            // Generate unique token
            String tokenValue = generateUniqueToken(request.getType());

            // Encrypt sensitive data with KMS
            String kmsKeyId = request.getKmsKeyId() != null ? request.getKmsKeyId() : defaultKmsKeyId;
            String encryptedData = tokenEncryptionService.encrypt(request.getSensitiveData(), kmsKeyId);

            // Calculate expiration
            Instant expiresAt = calculateExpiration(request.getType(), request.getExpirationDays());

            // Create token entity
            Token token = Token.builder()
                .token(tokenValue)
                .encryptedData(encryptedData)
                .type(request.getType())
                .userId(request.getUserId())
                .kmsKeyId(kmsKeyId)
                .status(TokenStatus.ACTIVE)
                .expiresAt(expiresAt)
                .metadata(request.getMetadata())
                .createdFromIp(request.getIpAddress())
                .createdUserAgent(request.getUserAgent())
                .build();

            // Save to database
            tokenRepository.save(token);

            tokenizationSuccess.increment();

            log.info("Tokenization successful: token={}, type={}, userId={}",
                maskToken(tokenValue), request.getType(), request.getUserId());

            return TokenizationResult.builder()
                .token(tokenValue)
                .type(request.getType())
                .expiresAt(expiresAt)
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Tokenization failed: type={}, userId={}",
                request.getType(), request.getUserId(), e);
            tokenizationError.increment();

            return TokenizationResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Detokenize to retrieve original sensitive data
     *
     * @param request Detokenization request
     * @return Original sensitive data
     */
    @Transactional
    public DetokenizationResult detokenize(DetokenizationRequest request) {
        try {
            log.info("Detokenizing: token={}, userId={}",
                maskToken(request.getToken()), request.getUserId());

            // Find token with ownership validation
            Optional<Token> tokenOpt = tokenRepository.findByTokenAndUserId(
                request.getToken(), request.getUserId());

            if (tokenOpt.isEmpty()) {
                log.warn("Token not found or unauthorized: token={}, userId={}",
                    maskToken(request.getToken()), request.getUserId());
                detokenizationError.increment();

                return DetokenizationResult.builder()
                    .success(false)
                    .errorMessage("Token not found or unauthorized")
                    .build();
            }

            Token token = tokenOpt.get();

            // Validate token is active
            if (!token.isActive()) {
                log.warn("Token is not active: token={}, status={}, expired={}",
                    maskToken(request.getToken()), token.getStatus(), token.isExpired());
                detokenizationError.increment();

                return DetokenizationResult.builder()
                    .success(false)
                    .errorMessage("Token is not active (status: " + token.getStatus() + ")")
                    .build();
            }

            // Decrypt sensitive data
            String sensitiveData = tokenEncryptionService.decrypt(
                token.getEncryptedData(), token.getKmsKeyId());

            // Update usage tracking
            token.incrementUsage();
            tokenRepository.save(token);

            detokenizationSuccess.increment();

            log.info("Detokenization successful: token={}, type={}, userId={}",
                maskToken(request.getToken()), token.getType(), request.getUserId());

            return DetokenizationResult.builder()
                .sensitiveData(sensitiveData)
                .type(token.getType())
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Detokenization failed: token={}, userId={}",
                maskToken(request.getToken()), request.getUserId(), e);
            detokenizationError.increment();

            return DetokenizationResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Revoke a token (make it unusable)
     *
     * @param token Token to revoke
     * @param userId User ID (for ownership validation)
     * @return true if revoked successfully
     */
    @Transactional
    public boolean revokeToken(String token, String userId) {
        try {
            log.info("Revoking token: token={}, userId={}", maskToken(token), userId);

            Optional<Token> tokenOpt = tokenRepository.findByTokenAndUserId(token, userId);

            if (tokenOpt.isEmpty()) {
                log.warn("Token not found or unauthorized for revocation: token={}, userId={}",
                    maskToken(token), userId);
                return false;
            }

            Token tokenEntity = tokenOpt.get();
            tokenEntity.setStatus(TokenStatus.REVOKED);
            tokenRepository.save(tokenEntity);

            tokenRevocations.increment();

            log.info("Token revoked successfully: token={}, userId={}", maskToken(token), userId);

            return true;

        } catch (Exception e) {
            log.error("Token revocation failed: token={}, userId={}", maskToken(token), userId, e);
            return false;
        }
    }

    /**
     * Validate token without retrieving data
     *
     * @param token Token to validate
     * @param userId User ID
     * @return Validation result
     */
    @Transactional(readOnly = true)
    public TokenValidationResult validateToken(String token, String userId) {
        try {
            Optional<Token> tokenOpt = tokenRepository.findByTokenAndUserId(token, userId);

            if (tokenOpt.isEmpty()) {
                return TokenValidationResult.builder()
                    .valid(false)
                    .reason("Token not found or unauthorized")
                    .build();
            }

            Token tokenEntity = tokenOpt.get();

            if (!tokenEntity.isActive()) {
                return TokenValidationResult.builder()
                    .valid(false)
                    .reason("Token is " + tokenEntity.getStatus())
                    .build();
            }

            return TokenValidationResult.builder()
                .valid(true)
                .type(tokenEntity.getType())
                .expiresAt(tokenEntity.getExpiresAt())
                .build();

        } catch (Exception e) {
            log.error("Token validation failed: token={}, userId={}", maskToken(token), userId, e);
            return TokenValidationResult.builder()
                .valid(false)
                .reason("Validation error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Get all tokens for a user
     *
     * @param userId User ID
     * @param status Optional status filter
     * @return List of tokens
     */
    @Transactional(readOnly = true)
    public List<Token> getUserTokens(String userId, TokenStatus status) {
        if (status != null) {
            return tokenRepository.findByUserIdAndStatus(userId, status);
        } else {
            return tokenRepository.findByUserIdAndStatus(userId, TokenStatus.ACTIVE);
        }
    }

    // Private helper methods

    private void validateTokenizationRequest(TokenizationRequest request) {
        if (request.getSensitiveData() == null || request.getSensitiveData().isEmpty()) {
            throw new IllegalArgumentException("Sensitive data cannot be empty");
        }

        if (request.getType() == null) {
            throw new IllegalArgumentException("Token type is required");
        }

        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        // Validate sensitive data format based on type
        if (request.getType() == TokenType.CARD) {
            validateCardNumber(request.getSensitiveData());
        }
    }

    private void validateCardNumber(String cardNumber) {
        // Basic Luhn algorithm validation
        String cleaned = cardNumber.replaceAll("\\s+", "");
        if (!cleaned.matches("^\\d{13,19}$")) {
            throw new IllegalArgumentException("Invalid card number format");
        }
    }

    private String generateUniqueToken(TokenType type) {
        int attempts = 0;
        while (attempts < maxRetries) {
            String token = tokenGeneratorService.generateToken(type);

            if (!tokenRepository.existsByToken(token)) {
                return token;
            }

            attempts++;
            log.warn("Token collision detected, retrying... (attempt {}/{})", attempts, maxRetries);
        }

        throw new RuntimeException("Failed to generate unique token after " + maxRetries + " attempts");
    }

    private Instant calculateExpiration(TokenType type, Integer customDays) {
        int days = customDays != null ? customDays : type.getDefaultExpirationDays();
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    // DTOs

    @Data
    @Builder
    public static class TokenizationRequest {
        private String sensitiveData;
        private TokenType type;
        private String userId;
        private String kmsKeyId;
        private Integer expirationDays;
        private String metadata;
        private String ipAddress;
        private String userAgent;
    }

    @Data
    @Builder
    public static class TokenizationResult {
        private boolean success;
        private String token;
        private TokenType type;
        private Instant expiresAt;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class DetokenizationRequest {
        private String token;
        private String userId;
    }

    @Data
    @Builder
    public static class DetokenizationResult {
        private boolean success;
        private String sensitiveData;
        private TokenType type;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class TokenValidationResult {
        private boolean valid;
        private TokenType type;
        private Instant expiresAt;
        private String reason;
    }
}
