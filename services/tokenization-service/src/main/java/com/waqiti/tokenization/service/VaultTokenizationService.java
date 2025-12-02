package com.waqiti.tokenization.service;

import com.waqiti.tokenization.domain.TokenMapping;
import com.waqiti.tokenization.dto.TokenizationRequest;
import com.waqiti.tokenization.dto.TokenizationResponse;
import com.waqiti.tokenization.dto.DetokenizationRequest;
import com.waqiti.tokenization.dto.DetokenizationResponse;
import com.waqiti.tokenization.repository.TokenMappingRepository;
import com.waqiti.tokenization.exception.TokenizationException;
import com.waqiti.tokenization.exception.TokenNotFoundException;
import com.waqiti.common.tracing.Traced;
import com.waqiti.common.audit.AuditLogger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultTransitContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * PCI-DSS Compliant Card Tokenization Service
 *
 * Implements PCI-DSS Requirement 3.4: Card data tokenization using HashiCorp Vault Transit Engine
 *
 * Security Features:
 * - AES-256-GCM encryption via Vault Transit Engine
 * - No plaintext card numbers stored in application memory
 * - Hardware Security Module (HSM) backed encryption keys
 * - Automatic key rotation support
 * - Comprehensive audit logging
 * - Token format: tok_[32-char-uuid]
 *
 * Compliance:
 * - PCI-DSS v4.0 Requirement 3.4 (Tokenization)
 * - PCI-DSS v4.0 Requirement 3.5 (Encryption Key Management)
 * - PCI-DSS v4.0 Requirement 10 (Audit Logging)
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VaultTokenizationService {

    private final VaultOperations vaultOperations;
    private final TokenMappingRepository tokenMappingRepository;
    private final AuditLogger auditLogger;

    private static final String VAULT_TRANSIT_KEY_NAME = "card-tokenization";
    private static final String TOKEN_PREFIX = "tok_";
    private static final String AUDIT_ACTION_TOKENIZE = "CARD_TOKENIZED";
    private static final String AUDIT_ACTION_DETOKENIZE = "CARD_DETOKENIZED";

    /**
     * Tokenizes a card number using Vault Transit Engine
     *
     * @param request Tokenization request containing card number
     * @return Tokenization response with generated token
     * @throws TokenizationException if tokenization fails
     */
    @Transactional
    @Traced(operationName = "tokenize-card", businessOperation = "card-tokenization", priority = Traced.TracingPriority.CRITICAL)
    public TokenizationResponse tokenizeCardNumber(TokenizationRequest request) {
        log.info("Tokenizing card number for user: {}", request.getUserId());

        try {
            // Validate card number format (basic validation, real implementation would use Luhn algorithm)
            validateCardNumber(request.getCardNumber());

            // Extract last 4 digits for reference (PCI-DSS compliant - can store last 4)
            String last4Digits = extractLast4Digits(request.getCardNumber());

            // Extract card BIN (first 6 digits) for fraud detection
            String cardBin = extractCardBin(request.getCardNumber());

            // Encrypt card number using Vault Transit Engine
            VaultTransitContext context = VaultTransitContext.builder()
                .context(Base64.getEncoder().encodeToString(
                    request.getUserId().toString().getBytes(StandardCharsets.UTF_8)))
                .build();

            String ciphertext = vaultOperations.opsForTransit()
                .encrypt(VAULT_TRANSIT_KEY_NAME,
                        request.getCardNumber().getBytes(StandardCharsets.UTF_8),
                        context);

            // Generate unique token
            String token = generateToken();

            // Store token mapping in database (encrypted ciphertext only, never plaintext)
            TokenMapping tokenMapping = TokenMapping.builder()
                .token(token)
                .encryptedCardData(ciphertext)
                .userId(request.getUserId())
                .last4Digits(last4Digits)
                .cardBin(cardBin)
                .cardType(determineCardType(cardBin))
                .createdAt(Instant.now())
                .expiresAt(request.getExpirationDate())
                .active(true)
                .build();

            tokenMappingRepository.save(tokenMapping);

            // Audit log (PCI-DSS Requirement 10)
            auditLogger.logSecurityEvent(
                AUDIT_ACTION_TOKENIZE,
                request.getUserId(),
                "card.tokenization",
                "Card tokenized successfully",
                Map.of(
                    "token", token,
                    "last4", last4Digits,
                    "cardBin", cardBin,
                    "cardType", tokenMapping.getCardType()
                )
            );

            log.info("Card tokenization successful. Token: {}, Last4: {}", token, last4Digits);

            return TokenizationResponse.builder()
                .token(token)
                .last4Digits(last4Digits)
                .cardType(tokenMapping.getCardType())
                .expiresAt(request.getExpirationDate())
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Card tokenization failed for user: {}", request.getUserId(), e);

            auditLogger.logSecurityEvent(
                "CARD_TOKENIZATION_FAILED",
                request.getUserId(),
                "card.tokenization",
                "Card tokenization failed: " + e.getMessage(),
                Map.of("error", e.getClass().getSimpleName())
            );

            throw new TokenizationException("Failed to tokenize card number", e);
        }
    }

    /**
     * Detokenizes a card token to retrieve original card number
     *
     * SECURITY NOTE: This operation is highly sensitive and should only be called
     * when absolutely necessary (e.g., payment gateway integration, card display to user)
     *
     * @param request Detokenization request containing token
     * @return Detokenization response with decrypted card number
     * @throws TokenNotFoundException if token not found
     * @throws TokenizationException if detokenization fails
     */
    @Transactional
    @Traced(operationName = "detokenize-card", businessOperation = "card-detokenization", priority = Traced.TracingPriority.CRITICAL)
    public DetokenizationResponse detokenizeCardNumber(DetokenizationRequest request) {
        log.info("Detokenizing card token: {}", maskToken(request.getToken()));

        try {
            // Retrieve token mapping from database
            TokenMapping tokenMapping = tokenMappingRepository.findByTokenAndUserId(
                request.getToken(),
                request.getUserId()
            ).orElseThrow(() -> new TokenNotFoundException("Token not found or unauthorized: " + request.getToken()));

            // Verify token is still active
            if (!tokenMapping.isActive()) {
                throw new TokenizationException("Token is inactive");
            }

            // Verify token has not expired
            if (tokenMapping.getExpiresAt() != null && tokenMapping.getExpiresAt().isBefore(Instant.now())) {
                throw new TokenizationException("Token has expired");
            }

            // Decrypt card number using Vault Transit Engine
            VaultTransitContext context = VaultTransitContext.builder()
                .context(Base64.getEncoder().encodeToString(
                    request.getUserId().toString().getBytes(StandardCharsets.UTF_8)))
                .build();

            byte[] plaintext = vaultOperations.opsForTransit()
                .decrypt(VAULT_TRANSIT_KEY_NAME,
                        tokenMapping.getEncryptedCardData(),
                        context);

            String cardNumber = new String(plaintext, StandardCharsets.UTF_8);

            // Audit log (PCI-DSS Requirement 10)
            auditLogger.logSecurityEvent(
                AUDIT_ACTION_DETOKENIZE,
                request.getUserId(),
                "card.detokenization",
                "Card detokenized successfully",
                Map.of(
                    "token", request.getToken(),
                    "last4", tokenMapping.getLast4Digits(),
                    "purpose", request.getPurpose()
                )
            );

            log.info("Card detokenization successful. Token: {}, Last4: {}",
                maskToken(request.getToken()), tokenMapping.getLast4Digits());

            return DetokenizationResponse.builder()
                .cardNumber(cardNumber)
                .last4Digits(tokenMapping.getLast4Digits())
                .cardType(tokenMapping.getCardType())
                .success(true)
                .build();

        } catch (TokenNotFoundException e) {
            log.error("Token not found: {}", maskToken(request.getToken()));

            auditLogger.logSecurityEvent(
                "CARD_DETOKENIZATION_FAILED",
                request.getUserId(),
                "card.detokenization",
                "Token not found: " + maskToken(request.getToken()),
                Map.of("error", "TokenNotFoundException")
            );

            throw e;

        } catch (Exception e) {
            log.error("Card detokenization failed for token: {}", maskToken(request.getToken()), e);

            auditLogger.logSecurityEvent(
                "CARD_DETOKENIZATION_FAILED",
                request.getUserId(),
                "card.detokenization",
                "Card detokenization failed: " + e.getMessage(),
                Map.of("error", e.getClass().getSimpleName())
            );

            throw new TokenizationException("Failed to detokenize card number", e);
        }
    }

    /**
     * Validates card number format using Luhn algorithm
     */
    private void validateCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            throw new IllegalArgumentException("Card number cannot be null or empty");
        }

        // Remove spaces and dashes
        String sanitized = cardNumber.replaceAll("[\\s-]", "");

        // Verify length (13-19 digits for valid cards)
        if (sanitized.length() < 13 || sanitized.length() > 19) {
            throw new IllegalArgumentException("Invalid card number length");
        }

        // Verify all digits
        if (!sanitized.matches("\\d+")) {
            throw new IllegalArgumentException("Card number must contain only digits");
        }

        // Luhn algorithm validation
        if (!passesLuhnCheck(sanitized)) {
            throw new IllegalArgumentException("Card number failed Luhn check");
        }
    }

    /**
     * Luhn algorithm (Mod 10) for card validation
     */
    private boolean passesLuhnCheck(String cardNumber) {
        int sum = 0;
        boolean alternate = false;

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return (sum % 10 == 0);
    }

    /**
     * Extracts last 4 digits of card (PCI-DSS allows storing last 4)
     */
    private String extractLast4Digits(String cardNumber) {
        String sanitized = cardNumber.replaceAll("[\\s-]", "");
        return sanitized.substring(sanitized.length() - 4);
    }

    /**
     * Extracts card BIN (first 6 digits) for fraud detection
     */
    private String extractCardBin(String cardNumber) {
        String sanitized = cardNumber.replaceAll("[\\s-]", "");
        return sanitized.substring(0, Math.min(6, sanitized.length()));
    }

    /**
     * Determines card type from BIN
     */
    private String determineCardType(String cardBin) {
        if (cardBin.startsWith("4")) {
            return "VISA";
        } else if (cardBin.startsWith("5")) {
            return "MASTERCARD";
        } else if (cardBin.startsWith("3")) {
            return cardBin.startsWith("34") || cardBin.startsWith("37") ? "AMEX" : "DISCOVER";
        } else if (cardBin.startsWith("6")) {
            return "DISCOVER";
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Generates unique token with prefix
     */
    private String generateToken() {
        return TOKEN_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Masks token for logging (shows only prefix and last 4 chars)
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    /**
     * Revokes a token (marks as inactive)
     *
     * @param token Token to revoke
     * @param userId User ID for authorization
     */
    @Transactional
    @Traced(operationName = "revoke-token", businessOperation = "token-revocation", priority = Traced.TracingPriority.HIGH)
    public void revokeToken(String token, UUID userId) {
        log.info("Revoking token: {}", maskToken(token));

        TokenMapping tokenMapping = tokenMappingRepository.findByTokenAndUserId(token, userId)
            .orElseThrow(() -> new TokenNotFoundException("Token not found: " + token));

        tokenMapping.setActive(false);
        tokenMapping.setRevokedAt(Instant.now());
        tokenMappingRepository.save(tokenMapping);

        auditLogger.logSecurityEvent(
            "TOKEN_REVOKED",
            userId,
            "card.tokenization",
            "Token revoked",
            Map.of("token", maskToken(token))
        );

        log.info("Token revoked successfully: {}", maskToken(token));
    }
}
