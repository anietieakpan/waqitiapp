// File: services/user-service/src/main/java/com/waqiti/user/service/MfaService.java
package com.waqiti.user.service;

import com.waqiti.user.client.NotificationServiceClient;
import com.waqiti.user.domain.MfaConfiguration;
import com.waqiti.user.domain.MfaMethod;
import com.waqiti.user.domain.MfaVerificationCode;
import com.waqiti.user.dto.MfaSetupResponse;
import com.waqiti.user.dto.TwoFactorNotificationRequest;
import com.waqiti.user.repository.MfaConfigurationRepository;
import com.waqiti.user.repository.MfaVerificationCodeRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService {

    private final MfaConfigurationRepository mfaConfigRepository;
    private final MfaVerificationCodeRepository verificationCodeRepository;
    private final NotificationServiceClient notificationServiceClient;

    @Value("${application.name:Waqiti Finance}")
    private String applicationName;

    // TOTP components
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    // For generating SMS/Email codes
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Setup TOTP (authenticator app) for a user
     */
    @Transactional
    public MfaSetupResponse setupTotp(UUID userId, String username) {
        log.info("Setting up TOTP for user: {}", userId);

        // Generate a secret key
        String secret = secretGenerator.generate();

        // Save or update the configuration
        MfaConfiguration config = mfaConfigRepository.findByUserIdAndMethod(userId, MfaMethod.TOTP)
                .orElse(MfaConfiguration.create(userId, MfaMethod.TOTP, secret));

        if (config.isVerified() && config.isEnabled()) {
            // Generate a new secret if already verified and enabled
            secret = secretGenerator.generate();
            config.updateSecret(secret);
            config.disable();
        } else if (config.getId() != null &&
                !config.getId().equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
            // If it exists but is not verified, update the secret
            config.updateSecret(secret);
        }

        mfaConfigRepository.save(config);

        // Generate QR code image
        String qrCodeImage = generateQrCodeImage(secret, username);

        return MfaSetupResponse.builder()
                .secret(secret)
                .qrCodeImage(qrCodeImage)
                .build();
    }





    /**
     * Verify TOTP setup using a code from the authenticator app
     */
    @Transactional
    public boolean verifyTotpSetup(UUID userId, String code) {
        log.info("Verifying TOTP setup for user: {}", userId);

        MfaConfiguration config = mfaConfigRepository.findByUserIdAndMethod(userId, MfaMethod.TOTP)
                .orElseThrow(() -> new IllegalStateException("TOTP not set up for user"));

        if (verifyTotp(config.getSecret(), code)) {
            config.markVerified();
            config.enable();
            mfaConfigRepository.save(config);
            return true;
        }

        return false;
    }

    /**
     * Setup SMS verification for a user
     */
    @Transactional
    public boolean setupSms(UUID userId, String phoneNumber) {
        log.info("Setting up SMS MFA for user: {}", userId);

        // Save or update the configuration
        MfaConfiguration config = mfaConfigRepository.findByUserIdAndMethod(userId, MfaMethod.SMS)
                .orElse(MfaConfiguration.create(userId, MfaMethod.SMS, phoneNumber));

        config.updateSecret(phoneNumber); // Store phone number as the "secret"
        mfaConfigRepository.save(config);

        // Send verification code
        String code = generateAndSaveVerificationCode(userId, MfaMethod.SMS);

        // In a real implementation, send the SMS
        // For now, we'll use the notification service as a placeholder
        try {
            TwoFactorNotificationRequest request = TwoFactorNotificationRequest.builder()
                    .userId(userId)
                    .recipient(phoneNumber)
                    .verificationCode(code)
                    .language("en") // Default language
                    .build();

            return notificationServiceClient.sendTwoFactorSms(request);
        } catch (Exception e) {
            log.error("Failed to send SMS verification code", e);
            return false;
        }
    }

    /**
     * Setup email verification for a user
     */
    @Transactional
    public boolean setupEmail(UUID userId, String email) {
        log.info("Setting up Email MFA for user: {}", userId);

        // Save or update the configuration
        MfaConfiguration config = mfaConfigRepository.findByUserIdAndMethod(userId, MfaMethod.EMAIL)
                .orElse(MfaConfiguration.create(userId, MfaMethod.EMAIL, email));

        config.updateSecret(email); // Store email as the "secret"
        mfaConfigRepository.save(config);

        // Send verification code
        String code = generateAndSaveVerificationCode(userId, MfaMethod.EMAIL);

        // In a real implementation, send the email
        // For now, we'll use the notification service as a placeholder
        try {
            TwoFactorNotificationRequest request = TwoFactorNotificationRequest.builder()
                    .userId(userId)
                    .recipient(email)
                    .verificationCode(code)
                    .language("en") // Default language
                    .build();

            return notificationServiceClient.sendTwoFactorEmail(request);
        } catch (Exception e) {
            log.error("Failed to send Email verification code", e);
            return false;
        }
    }

    /**
     * Resend verification code for SMS or Email
     */
    @Transactional
    public boolean resendVerificationCode(UUID userId, MfaMethod method) {
        log.info("Resending verification code for user: {} and method: {}", userId, method);

        if (method != MfaMethod.SMS && method != MfaMethod.EMAIL) {
            throw new IllegalArgumentException("Method must be SMS or EMAIL for verification codes");
        }

        MfaConfiguration config = mfaConfigRepository.findByUserIdAndMethod(userId, method)
                .orElseThrow(() -> new IllegalStateException(method + " not set up for user"));

        String code = generateAndSaveVerificationCode(userId, method);

        try {
            TwoFactorNotificationRequest request = TwoFactorNotificationRequest.builder()
                    .userId(userId)
                    .recipient(config.getSecret()) // Phone number or email stored as secret
                    .verificationCode(code)
                    .language("en") // Default language
                    .build();

            if (method == MfaMethod.SMS) {
                return notificationServiceClient.sendTwoFactorSms(request);
            } else {
                return notificationServiceClient.sendTwoFactorEmail(request);
            }
        } catch (Exception e) {
            log.error("Failed to resend verification code", e);
            return false;
        }

    }

    /**
     * Verify SMS or Email setup using a code
     */
    @Transactional
    public boolean verifyCodeSetup(UUID userId, MfaMethod method, String code) {
        log.info("Verifying code setup for user: {} and method: {}", userId, method);

        MfaConfiguration config = mfaConfigRepository.findByUserIdAndMethod(userId, method)
                .orElseThrow(() -> new IllegalStateException(method + " not set up for user"));

        if (verifyCode(userId, method, code)) {
            config.markVerified();
            config.enable();
            mfaConfigRepository.save(config);
            return true;
        }

        return false;
    }

    /**
     * Disable an MFA method
     */
    @Transactional
    public boolean disableMfaMethod(UUID userId, MfaMethod method) {
        log.info("Disabling MFA method: {} for user: {}", method, userId);

        MfaConfiguration config = mfaConfigRepository.findByUserIdAndMethod(userId, method)
                .orElseThrow(() -> new IllegalStateException(method + " not set up for user"));

        config.disable();
        mfaConfigRepository.save(config);
        return true;
    }

    /**
     * Check if a user has MFA enabled
     */
    @Transactional(readOnly = true)
    public boolean isMfaEnabled(UUID userId) {
        return mfaConfigRepository.existsByUserIdAndEnabledTrueAndVerifiedTrue(userId);
    }

    /**
     * Get all enabled MFA methods for a user
     */
    @Transactional(readOnly = true)
    public List<MfaMethod> getEnabledMfaMethods(UUID userId) {
        return mfaConfigRepository.findByUserIdAndEnabledTrue(userId).stream()
                .filter(MfaConfiguration::isVerified)
                .map(MfaConfiguration::getMethod)
                .collect(Collectors.toList());
    }

    /**
     * Verify an MFA code during login
     */
    @Transactional
    public boolean verifyMfaCode(UUID userId, MfaMethod method, String code) {
        log.info("Verifying MFA code for user: {} and method: {}", userId, method);

        if (method == MfaMethod.TOTP) {
            MfaConfiguration config = mfaConfigRepository.findByUserIdAndMethodAndEnabledTrueAndVerifiedTrue(userId, method)
                    .orElseThrow(() -> new IllegalStateException("TOTP not enabled for user"));

            return verifyTotp(config.getSecret(), code);
        } else {
            return verifyCode(userId, method, code);
        }
    }

    /**
     * Generate MFA verification code for SMS or Email
     */
    private String generateAndSaveVerificationCode(UUID userId, MfaMethod method) {
        // Generate a 6-digit code
        String code = String.format("%06d", secureRandom.nextInt(1000000));

        // Save the code
        MfaVerificationCode verificationCode = MfaVerificationCode.create(
                userId, method, code, 5 // 5 minutes expiry
        );

        verificationCodeRepository.save(verificationCode);
        return code;
    }

    /**
     * Verify a TOTP code
     */
    private boolean verifyTotp(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }

    /**
     * Verify an SMS or Email code
     */
    private boolean verifyCode(UUID userId, MfaMethod method, String code) {
        Optional<MfaVerificationCode> verificationCode = verificationCodeRepository
                .findByUserIdAndMethodAndCodeAndUsedFalseAndExpiryDateAfter(
                        userId, method, code, LocalDateTime.now());

        if (verificationCode.isPresent()) {
            MfaVerificationCode vc = verificationCode.get();
            if (vc.isValid()) {
                vc.markUsed();
                verificationCodeRepository.save(vc);
                return true;
            }
        }

        return false;
    }

    /**
     * Generate QR code image for TOTP setup
     */
    private String generateQrCodeImage(String secret, String username) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(applicationName)
                .algorithm(HashingAlgorithm.SHA256)
                .digits(6)
                .period(30)
                .build();

        try {
            byte[] imageData = qrGenerator.generate(data);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageData);
        } catch (QrGenerationException e) {
            log.error("Error generating QR code", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Generates recovery codes for a user
     * @param userId The user ID
     * @param count Number of recovery codes to generate
     * @return Array of recovery code UUIDs
     */
    @Transactional
    public UUID[] generateRecoveryCodes(UUID userId, int count) {
        log.info("Generating {} recovery codes for user: {}", count, userId);

        // Check if user has MFA enabled
        if (!isMfaEnabled(userId)) {
            throw new IllegalStateException("MFA must be enabled to generate recovery codes");
        }

        // Generate random recovery codes
        UUID[] recoveryCodes = new UUID[count];
        for (int i = 0; i < count; i++) {
            recoveryCodes[i] = UUID.randomUUID();

            // Store the recovery code hash in the database
            // We're using MfaVerificationCode to store recovery codes with a special method type
            MfaVerificationCode recoveryCode = MfaVerificationCode.create(
                    userId,
                    MfaMethod.RECOVERY_CODE,
                    recoveryCodes[i].toString(),
                    365 * 24 * 60 // Valid for 1 year in minutes
            );
            verificationCodeRepository.save(recoveryCode);
        }

        return recoveryCodes;
    }

    /**
     * Verifies a recovery code for a user
     * @param userId The user ID
     * @param recoveryCode The recovery code to verify
     * @return True if recovery code is valid
     */
    @Transactional
    public boolean verifyRecoveryCode(UUID userId, String recoveryCode) {
        log.info("Verifying recovery code for user: {}", userId);

        // Find the recovery code
        Optional<MfaVerificationCode> code = verificationCodeRepository
                .findByUserIdAndMethodAndCodeAndUsedFalseAndExpiryDateAfter(
                        userId, MfaMethod.RECOVERY_CODE, recoveryCode, LocalDateTime.now());

        if (code.isPresent()) {
            MfaVerificationCode rc = code.get();
            if (rc.isValid()) {
                // Mark recovery code as used
                rc.markUsed();
                verificationCodeRepository.save(rc);

                // Disable MFA for the user since they're using a recovery code
                // This is a common pattern - after recovery, user needs to set up MFA again
                mfaConfigRepository.findByUserIdAndEnabledTrue(userId)
                        .forEach(config -> {
                            config.disable();
                            mfaConfigRepository.save(config);
                        });

                return true;
            }
        }

        return false;
    }


    /**
     * Resets all MFA configurations for a user (admin function)
     * @param userId The user ID
     * @return True if reset was successful
     */
    @Transactional
    public boolean resetUserMfa(UUID userId) {
        log.info("Resetting MFA for user: {}", userId);

        // Find all MFA configurations for the user and disable them
        List<MfaConfiguration> configs = mfaConfigRepository.findByUserId(userId);
        for (MfaConfiguration config : configs) {
            config.disable();
        }
        mfaConfigRepository.saveAll(configs);

        // Delete any verification codes
        verificationCodeRepository.deleteByUserId(userId);

        return true;
    }


    /**
     * Cleanup expired verification codes
     */
    @Scheduled(cron = "0 0 * * * *") // Run every hour
    @Transactional
    public void cleanupExpiredCodes() {
        log.info("Cleaning up expired verification codes");

        List<MfaVerificationCode> expiredCodes = verificationCodeRepository
                .findByUsedFalseAndExpiryDateBefore(LocalDateTime.now());

        for (MfaVerificationCode code : expiredCodes) {
            code.markUsed();
        }

        verificationCodeRepository.saveAll(expiredCodes);
        log.info("Marked {} expired verification codes as used", expiredCodes.size());
    }
}