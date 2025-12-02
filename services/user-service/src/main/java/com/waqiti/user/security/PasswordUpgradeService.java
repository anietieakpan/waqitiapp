package com.waqiti.user.security;

import com.waqiti.user.entity.User;
import com.waqiti.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Password Upgrade Service
 *
 * Transparently upgrades password hashes from BCrypt 12 rounds to 14 rounds
 * for enhanced security against brute-force attacks.
 *
 * SECURITY CONTEXT:
 * - Previous: 12 rounds (4,096 iterations) - ~400ms per hash
 * - Current: 14 rounds (16,384 iterations) - ~1,600ms per hash
 * - Benefit: 4x more resistant to brute-force attacks
 *
 * UPGRADE STRATEGY:
 * 1. Upgrade happens transparently during user login
 * 2. User's plaintext password is available during authentication
 * 3. Re-hash with stronger algorithm and update database
 * 4. No user action required - completely transparent
 *
 * COMPLIANCE:
 * - NIST SP 800-63B recommendation: 14+ rounds for high-value systems
 * - OWASP ASVS Level 3: Adaptive password hashing with work factor
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordUpgradeService {

    private static final int MINIMUM_BCRYPT_ROUNDS = 14;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Check if user's password needs upgrade and upgrade it
     *
     * IMPORTANT: This should be called AFTER successful authentication,
     * when we have access to the plaintext password.
     *
     * @param user Authenticated user
     * @param rawPassword Plaintext password from login (before it's cleared from memory)
     */
    @Transactional
    public void upgradePasswordIfNeeded(User user, String rawPassword) {
        if (user == null || rawPassword == null) {
            return;
        }

        try {
            if (needsUpgrade(user.getPasswordHash())) {
                upgradePassword(user, rawPassword);
            }
        } catch (Exception e) {
            // Don't fail login if upgrade fails - log and continue
            log.error("SECURITY: Failed to upgrade password for user {} - will retry on next login",
                    user.getId(), e);
        }
    }

    /**
     * Asynchronous password upgrade for batch processing
     * Used when user logs in - doesn't block the authentication response
     */
    @Async("passwordUpgradeExecutor")
    @Transactional
    public CompletableFuture<Void> upgradePasswordAsync(User user, String rawPassword) {
        return CompletableFuture.runAsync(() -> upgradePasswordIfNeeded(user, rawPassword));
    }

    /**
     * Check if password hash needs upgrade
     *
     * BCrypt hash format: $2a$ROUNDS$salthash
     * Example: $2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW
     *                  ^^
     *           These are the rounds
     */
    public boolean needsUpgrade(String passwordHash) {
        if (passwordHash == null || passwordHash.isEmpty()) {
            return false;
        }

        try {
            // Extract rounds from BCrypt hash
            int currentRounds = extractBCryptRounds(passwordHash);

            if (currentRounds < MINIMUM_BCRYPT_ROUNDS) {
                log.info("SECURITY: Password hash needs upgrade: current={} rounds, minimum={} rounds",
                        currentRounds, MINIMUM_BCRYPT_ROUNDS);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.warn("SECURITY: Unable to parse BCrypt hash format - may be corrupted", e);
            return false;
        }
    }

    /**
     * Extract BCrypt rounds from hash string
     *
     * @param hash BCrypt hash
     * @return Number of rounds used
     * @throws IllegalArgumentException if hash format is invalid
     */
    private int extractBCryptRounds(String hash) {
        if (hash == null || !hash.startsWith("$2")) {
            throw new IllegalArgumentException("Invalid BCrypt hash format");
        }

        // BCrypt format: $2a$12$salthash...
        // Split by $ and get rounds (index 2)
        String[] parts = hash.split("\\$");

        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid BCrypt hash format - insufficient parts");
        }

        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid BCrypt rounds: " + parts[2], e);
        }
    }

    /**
     * Perform the password upgrade
     */
    @Transactional
    protected void upgradePassword(User user, String rawPassword) {
        int oldRounds = extractBCryptRounds(user.getPasswordHash());

        // Generate new hash with stronger rounds
        String newHash = passwordEncoder.encode(rawPassword);
        int newRounds = extractBCryptRounds(newHash);

        // Update user record
        user.setPasswordHash(newHash);
        user.setPasswordUpgradedAt(LocalDateTime.now());
        user.setPasswordHashVersion(newRounds);

        userRepository.save(user);

        log.info("SECURITY: Password upgraded for user {}: {} rounds -> {} rounds ({}x stronger)",
                user.getId(), oldRounds, newRounds, Math.pow(2, newRounds - oldRounds));

        // Emit metric for monitoring
        logPasswordUpgradeMetric(user, oldRounds, newRounds);
    }

    /**
     * Get upgrade statistics for monitoring dashboard
     */
    @Transactional(readOnly = true)
    public PasswordUpgradeStats getUpgradeStats() {
        long totalUsers = userRepository.count();
        long upgradedUsers = userRepository.countByPasswordHashVersion(MINIMUM_BCRYPT_ROUNDS);
        long pendingUpgrades = totalUsers - upgradedUsers;

        double upgradePercentage = totalUsers > 0
            ? ((double) upgradedUsers / totalUsers) * 100
            : 0.0;

        return PasswordUpgradeStats.builder()
                .totalUsers(totalUsers)
                .upgradedUsers(upgradedUsers)
                .pendingUpgrades(pendingUpgrades)
                .upgradePercentage(upgradePercentage)
                .minimumRequiredRounds(MINIMUM_BCRYPT_ROUNDS)
                .build();
    }

    /**
     * Batch upgrade passwords for users who haven't logged in recently
     *
     * WARNING: This requires forcing password resets, should be done
     * with proper user notification and during maintenance window
     */
    @Transactional
    public int batchForcePasswordReset(int limit) {
        List<User> usersNeedingUpgrade = userRepository.findUsersNeedingPasswordUpgrade(
                MINIMUM_BCRYPT_ROUNDS,
                limit
        );

        int resetCount = 0;
        for (User user : usersNeedingUpgrade) {
            try {
                // Force password reset - user must set new password
                user.setPasswordResetRequired(true);
                user.setPasswordResetReason("Security upgrade: stronger password hashing");
                userRepository.save(user);

                resetCount++;

                log.info("SECURITY: Forced password reset for user {} due to hash upgrade",
                        user.getId());

            } catch (Exception e) {
                log.error("SECURITY: Failed to force password reset for user {}",
                        user.getId(), e);
            }
        }

        log.info("SECURITY: Batch password reset completed: {} users affected", resetCount);
        return resetCount;
    }

    /**
     * Log password upgrade metric for monitoring
     */
    private void logPasswordUpgradeMetric(User user, int oldRounds, int newRounds) {
        // This would integrate with Prometheus/Micrometer
        // For now, structured logging for analysis
        log.info("PASSWORD_UPGRADE_METRIC: userId={}, oldRounds={}, newRounds={}, timestamp={}",
                user.getId(), oldRounds, newRounds, LocalDateTime.now());
    }

    /**
     * Password upgrade statistics DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class PasswordUpgradeStats {
        private long totalUsers;
        private long upgradedUsers;
        private long pendingUpgrades;
        private double upgradePercentage;
        private int minimumRequiredRounds;
    }
}
