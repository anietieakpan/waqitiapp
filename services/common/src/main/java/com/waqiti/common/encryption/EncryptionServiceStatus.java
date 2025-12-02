package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

/**
 * Overall encryption service status including HSM information
 */
@Data
@Builder
public class EncryptionServiceStatus {
    private boolean hsmEnabled;
    private boolean hsmAvailable;
    private boolean hsmHealthy;
    private int currentKeyVersion;
    private int totalKeyVersions;
    private boolean auditEnabled;
    private String complianceLevel;
    private int keyRotationDays;
}