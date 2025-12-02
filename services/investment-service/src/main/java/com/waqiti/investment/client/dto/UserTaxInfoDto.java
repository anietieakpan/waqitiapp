package com.waqiti.investment.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User Tax Information DTO - Response from user-service
 *
 * CRITICAL SECURITY: Contains encrypted sensitive tax identification data.
 *
 * Data Classification: PII-SENSITIVE
 * - TIN/SSN is encrypted with AES-256
 * - Must be decrypted only in secure, isolated context
 * - All access is logged for compliance and audit
 *
 * Compliance:
 * - IRS Publication 1075 (Safeguarding Tax Information)
 * - IRC Section 6103 (Confidentiality and disclosure of returns and return information)
 * - GLBA (Gramm-Leach-Bliley Act) privacy requirements
 * - SOC 2 Type II data protection controls
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserTaxInfoDto {

    /**
     * Encrypted Tax Identification Number (SSN or EIN).
     * Format: Base64-encoded encrypted string
     *
     * CRITICAL: Must be decrypted using Vault encryption service
     */
    private String encryptedTin;

    /**
     * Tax ID type: "SSN" or "EIN"
     */
    private String tinType;

    /**
     * Backup withholding status (IRS Form W-9).
     * true = subject to backup withholding (24%)
     * false = not subject to backup withholding
     */
    private Boolean backupWithholdingRequired;

    /**
     * Foreign tax status.
     * true = taxpayer is a foreign person (Form W-8)
     * false = U.S. person (Form W-9)
     */
    private Boolean isForeignTaxpayer;

    /**
     * Tax residency country code (ISO 3166-1 alpha-2).
     * e.g., "US", "CA", "GB"
     */
    private String taxResidencyCountry;

    /**
     * W-9 certification status.
     * true = valid W-9 on file
     * false = W-9 not on file or expired
     */
    private Boolean w9OnFile;

    /**
     * FATCA status (Foreign Account Tax Compliance Act).
     * true = FATCA reporting required
     * false = FATCA not applicable
     */
    private Boolean fatcaReportingRequired;

    /**
     * Tax filing status: "SINGLE", "MARRIED_JOINT", "MARRIED_SEPARATE", "HEAD_OF_HOUSEHOLD", "WIDOW"
     */
    private String taxFilingStatus;

    /**
     * Masked TIN for display purposes (last 4 digits only).
     * Format: "XXX-XX-1234" or "XX-XXX1234"
     */
    private String maskedTin;

    /**
     * Check if TIN data is available and valid.
     */
    public boolean hasTin() {
        return encryptedTin != null && !encryptedTin.isBlank();
    }

    /**
     * Check if user requires backup withholding.
     */
    public boolean requiresBackupWithholding() {
        return backupWithholdingRequired != null && backupWithholdingRequired;
    }

    /**
     * Check if user is a U.S. taxpayer.
     */
    public boolean isUSTaxpayer() {
        return isForeignTaxpayer == null || !isForeignTaxpayer;
    }
}
