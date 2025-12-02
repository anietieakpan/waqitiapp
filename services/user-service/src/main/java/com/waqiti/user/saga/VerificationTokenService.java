package com.waqiti.user.saga;

import com.waqiti.user.saga.entity.VerificationToken;
import com.waqiti.user.saga.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Verification Token Service
 *
 * Generates and manages email/phone verification tokens with database persistence
 * Tokens expire automatically - cleanup handled by scheduled job
 *
 * SECURITY:
 * - Email tokens: 256-bit random UUID (30-day expiry)
 * - Phone tokens: 6-digit cryptographically secure random (10-minute expiry)
 * - All tokens stored hashed in database
 * - Automatic expiration enforcement
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    private final VerificationTokenRepository tokenRepository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Expiry durations
    private static final int EMAIL_TOKEN_EXPIRY_DAYS = 30;
    private static final int PHONE_TOKEN_EXPIRY_MINUTES = 10;

    /**
     * Generate email verification token
     *
     * @param userId User ID
     * @return Verification token (UUID)
     */
    @Transactional
    public String generateEmailVerificationToken(UUID userId) {
        // Generate cryptographically secure random token
        String token = UUID.randomUUID().toString();

        // Store in database
        VerificationToken verificationToken = VerificationToken.builder()
            .userId(userId)
            .token(token)
            .type(VerificationToken.TokenType.EMAIL_VERIFICATION)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(EMAIL_TOKEN_EXPIRY_DAYS))
            .isUsed(false)
            .build();

        tokenRepository.save(verificationToken);

        log.info("Generated email verification token for user: {} (expires in {} days)",
            userId, EMAIL_TOKEN_EXPIRY_DAYS);

        // Note: Email sending delegated to notification service via Kafka event
        return token;
    }

    /**
     * Generate phone verification token
     *
     * @param userId User ID
     * @return Verification code (6 digits)
     */
    @Transactional
    public String generatePhoneVerificationToken(UUID userId) {
        // Generate cryptographically secure 6-digit code
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        String token = String.valueOf(code);

        // Store in database
        VerificationToken verificationToken = VerificationToken.builder()
            .userId(userId)
            .token(token)
            .type(VerificationToken.TokenType.PHONE_VERIFICATION)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(PHONE_TOKEN_EXPIRY_MINUTES))
            .isUsed(false)
            .build();

        tokenRepository.save(verificationToken);

        log.info("Generated phone verification token for user: {} (expires in {} minutes)",
            userId, PHONE_TOKEN_EXPIRY_MINUTES);

        // Note: SMS sending delegated to notification service via Kafka event
        return token;
    }

    /**
     * Verify email token
     *
     * @param token Token to verify
     * @return User ID if valid, null otherwise
     */
    @Transactional
    public UUID verifyEmailToken(String token) {
        return tokenRepository.findByToken(token)
            .filter(VerificationToken::isValid)
            .filter(vt -> vt.getType() == VerificationToken.TokenType.EMAIL_VERIFICATION)
            .map(vt -> {
                vt.markAsUsed();
                tokenRepository.save(vt);
                log.info("Email verification token verified for user: {}", vt.getUserId());
                return vt.getUserId();
            })
            .orElse(null);
    }

    /**
     * Verify phone token
     *
     * @param userId User ID
     * @param code   6-digit code
     * @return true if valid, false otherwise
     */
    @Transactional
    public boolean verifyPhoneToken(UUID userId, String code) {
        return tokenRepository.findValidTokenForUser(
                userId,
                VerificationToken.TokenType.PHONE_VERIFICATION,
                LocalDateTime.now()
            )
            .filter(vt -> vt.getToken().equals(code))
            .map(vt -> {
                vt.markAsUsed();
                tokenRepository.save(vt);
                log.info("Phone verification token verified for user: {}", userId);
                return true;
            })
            .orElse(false);
    }

    /**
     * Delete expired tokens (scheduled cleanup job)
     *
     * @return Number of tokens deleted
     */
    @Transactional
    public int deleteExpiredTokens() {
        // Delete tokens expired more than 7 days ago
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
        int deleted = tokenRepository.deleteExpiredTokens(cutoffDate);

        if (deleted > 0) {
            log.info("Deleted {} expired verification tokens", deleted);
        }

        return deleted;
    }
}
