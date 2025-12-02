package com.waqiti.investment.tax.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.annotation.PostConstruct;
import java.util.regex.Pattern;

/**
 * IRS Credentials Service - Centralized management of IRS tax filing credentials
 *
 * Security Features:
 * - AWS Secrets Manager integration for production credentials
 * - Fallback to environment variables for development/staging
 * - Startup validation to prevent placeholder credentials in production
 * - Format validation for TCC, EIN, and TIN
 * - Fail-fast on invalid configuration
 *
 * Credentials Managed:
 * - Transmitter Control Code (TCC) - Required for FIRE electronic filing
 * - Transmitter EIN - Waqiti Inc Employer Identification Number
 * - Payer TIN - Tax Identification Number for 1099 forms
 *
 * @author Waqiti Platform - Tax Compliance Team
 * @version 1.0
 * @since 2025-10-11
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IRSCredentialsService {

    private final SecretsManagerClient secretsManagerClient;

    @Value("${waqiti.irs.transmitter-control-code:#{null}}")
    private String transmitterControlCodeFromEnv;

    @Value("${waqiti.irs.transmitter-ein:#{null}}")
    private String transmitterEinFromEnv;

    @Value("${waqiti.tax.payer.tin:#{null}}")
    private String payerTinFromEnv;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private String transmitterControlCode;
    private String transmitterEin;
    private String payerTin;

    // Validation patterns
    private static final Pattern EIN_PATTERN = Pattern.compile("^\\d{2}-\\d{7}$");
    private static final Pattern TCC_PATTERN = Pattern.compile("^[A-Z0-9]{5,10}$");
    private static final String[] PLACEHOLDER_VALUES = {
        "XXXXX", "XX-XXXXXXX", "99-9999999", "00-0000000", "12-3456789", "DEV00"
    };

    @PostConstruct
    public void init() {
        log.info("🔐 Initializing IRS Credentials Service (profile: {})", activeProfile);

        boolean isProduction = "production".equalsIgnoreCase(activeProfile);
        boolean isStaging = "staging".equalsIgnoreCase(activeProfile);

        try {
            // Load Transmitter Control Code
            loadTransmitterControlCode(isProduction);

            // Load Transmitter EIN
            loadTransmitterEin(isProduction);

            // Load Payer TIN
            loadPayerTin(isProduction);

            // Validate all credentials
            validateCredentials(isProduction, isStaging);

            log.info("✅ IRS Credentials successfully loaded and validated");
            log.info("   - TCC: {} (length: {})", maskCredential(transmitterControlCode),
                transmitterControlCode != null ? transmitterControlCode.length() : 0);
            log.info("   - EIN: {}", maskEin(transmitterEin));
            log.info("   - Payer TIN: {}", maskEin(payerTin));

        } catch (Exception e) {
            log.error("🔴 CRITICAL: Failed to initialize IRS credentials", e);
            throw new IllegalStateException("IRS credentials initialization failed: " + e.getMessage(), e);
        }
    }

    private void loadTransmitterControlCode(boolean isProduction) {
        try {
            // Try AWS Secrets Manager first (production/staging)
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId("waqiti/investment-service/irs/transmitter-control-code")
                .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            this.transmitterControlCode = response.secretString();
            log.info("✅ TCC loaded from AWS Secrets Manager");

        } catch (Exception e) {
            log.warn("⚠️ Failed to load TCC from AWS Secrets Manager: {}", e.getMessage());

            // Fallback to environment variable
            if (transmitterControlCodeFromEnv != null && !transmitterControlCodeFromEnv.isEmpty()) {
                this.transmitterControlCode = transmitterControlCodeFromEnv;
                log.info("✅ TCC loaded from environment variable");
            } else if (!isProduction) {
                log.warn("⚠️ Using default TCC for development (will be rejected by IRS)");
                this.transmitterControlCode = "DEV00";
            } else {
                throw new IllegalStateException("TCC must be configured in production");
            }
        }
    }

    private void loadTransmitterEin(boolean isProduction) {
        try {
            // Try AWS Secrets Manager first
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId("waqiti/investment-service/irs/transmitter-ein")
                .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            this.transmitterEin = response.secretString();
            log.info("✅ Transmitter EIN loaded from AWS Secrets Manager");

        } catch (Exception e) {
            log.warn("⚠️ Failed to load EIN from AWS Secrets Manager: {}", e.getMessage());

            // Fallback to environment variable
            if (transmitterEinFromEnv != null && !transmitterEinFromEnv.isEmpty()) {
                this.transmitterEin = transmitterEinFromEnv;
                log.info("✅ Transmitter EIN loaded from environment variable");
            } else if (!isProduction) {
                log.warn("⚠️ Using default EIN for development (will be rejected by IRS)");
                this.transmitterEin = "99-9999999";
            } else {
                throw new IllegalStateException("Transmitter EIN must be configured in production");
            }
        }
    }

    private void loadPayerTin(boolean isProduction) {
        try {
            // Try AWS Secrets Manager first
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId("waqiti/investment-service/irs/payer-tin")
                .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            this.payerTin = response.secretString();
            log.info("✅ Payer TIN loaded from AWS Secrets Manager");

        } catch (Exception e) {
            log.warn("⚠️ Failed to load Payer TIN from AWS Secrets Manager: {}", e.getMessage());

            // Fallback to environment variable
            if (payerTinFromEnv != null && !payerTinFromEnv.isEmpty()) {
                this.payerTin = payerTinFromEnv;
                log.info("✅ Payer TIN loaded from environment variable");
            } else if (!isProduction) {
                log.warn("⚠️ Using default Payer TIN for development (will be rejected by IRS)");
                this.payerTin = "99-9999999";
            } else {
                throw new IllegalStateException("Payer TIN must be configured in production");
            }
        }
    }

    private void validateCredentials(boolean isProduction, boolean isStaging) {
        // Validate TCC format
        if (transmitterControlCode == null || !TCC_PATTERN.matcher(transmitterControlCode).matches()) {
            String error = String.format("Invalid TCC format: %s (expected: 5-10 alphanumeric characters)",
                maskCredential(transmitterControlCode));
            if (isProduction || isStaging) {
                throw new IllegalStateException(error);
            } else {
                log.warn("⚠️ {}", error);
            }
        }

        // Validate EIN format
        if (transmitterEin == null || !EIN_PATTERN.matcher(transmitterEin).matches()) {
            String error = String.format("Invalid Transmitter EIN format: %s (expected: XX-XXXXXXX)",
                maskEin(transmitterEin));
            if (isProduction || isStaging) {
                throw new IllegalStateException(error);
            } else {
                log.warn("⚠️ {}", error);
            }
        }

        // Validate Payer TIN format
        if (payerTin == null || !EIN_PATTERN.matcher(payerTin).matches()) {
            String error = String.format("Invalid Payer TIN format: %s (expected: XX-XXXXXXX)",
                maskEin(payerTin));
            if (isProduction || isStaging) {
                throw new IllegalStateException(error);
            } else {
                log.warn("⚠️ {}", error);
            }
        }

        // Check for placeholder values in production/staging
        if (isProduction || isStaging) {
            checkForPlaceholders(transmitterControlCode, "TCC");
            checkForPlaceholders(transmitterEin, "Transmitter EIN");
            checkForPlaceholders(payerTin, "Payer TIN");
        }
    }

    private void checkForPlaceholders(String credential, String credentialName) {
        if (credential == null) {
            throw new IllegalStateException(credentialName + " is null in production environment");
        }

        for (String placeholder : PLACEHOLDER_VALUES) {
            if (credential.contains(placeholder)) {
                throw new IllegalStateException(String.format(
                    "🔴 CRITICAL: Placeholder value detected in %s: %s. " +
                    "Real IRS credentials MUST be configured in production. " +
                    "See IRS_CREDENTIALS_CONFIGURATION_GUIDE.md for setup instructions.",
                    credentialName, placeholder));
            }
        }
    }

    private String maskCredential(String credential) {
        if (credential == null || credential.length() < 4) {
            return "****";
        }
        return credential.substring(0, 2) + "***" + credential.substring(credential.length() - 2);
    }

    private String maskEin(String ein) {
        if (ein == null || !EIN_PATTERN.matcher(ein).matches()) {
            return "**-*******";
        }
        return ein.substring(0, 3) + "***" + ein.substring(7);
    }

    // Public getters

    public String getTransmitterControlCode() {
        if (transmitterControlCode == null) {
            throw new IllegalStateException("TCC not initialized");
        }
        return transmitterControlCode;
    }

    public String getTransmitterEin() {
        if (transmitterEin == null) {
            throw new IllegalStateException("Transmitter EIN not initialized");
        }
        return transmitterEin;
    }

    public String getPayerTin() {
        if (payerTin == null) {
            throw new IllegalStateException("Payer TIN not initialized");
        }
        return payerTin;
    }

    /**
     * Validate that credentials are properly configured before attempting IRS filing
     *
     * @throws IllegalStateException if credentials are invalid
     */
    public void validateBeforeFiling() {
        if (transmitterControlCode == null || transmitterEin == null || payerTin == null) {
            throw new IllegalStateException("IRS credentials not fully initialized. Cannot proceed with filing.");
        }

        boolean isProduction = "production".equalsIgnoreCase(activeProfile);
        if (isProduction) {
            checkForPlaceholders(transmitterControlCode, "TCC");
            checkForPlaceholders(transmitterEin, "Transmitter EIN");
            checkForPlaceholders(payerTin, "Payer TIN");
        }

        log.info("✅ IRS credentials validated for filing");
    }
}
