package com.waqiti.common.security.hsm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Configuration class for HSM providers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HSMConfig {
    
    private String configName;
    private String libraryPath;
    private int slotId;
    private char[] pin;
    private String providerType;
    private Map<String, Object> additionalProperties;
    private boolean enableFIPS;
    private boolean enableAuditLogging;
    private int sessionTimeout;
    private int maxRetries;
    
    public HSMConfig(String configName, String libraryPath, int slotId, char[] pin) {
        this.configName = configName;
        this.libraryPath = libraryPath;
        this.slotId = slotId;
        this.pin = pin;
        this.enableFIPS = true;
        this.enableAuditLogging = true;
        this.sessionTimeout = 300; // 5 minutes
        this.maxRetries = 3;
    }
    
    /**
     * Get HSM password (alias for pin field)
     * @return HSM password as char array
     */
    public char[] getHsmPassword() {
        return pin;
    }

    // Thales-specific configuration methods
    private String thalesSecurityWorldPath;
    private String[] thalesHsmAddresses;
    private String thalesOperatorCardSet;
    private String thalesAdministratorCardSet;

    public String getThalesSecurityWorldPath() {
        return thalesSecurityWorldPath != null ? thalesSecurityWorldPath :
            (additionalProperties != null ? (String) additionalProperties.get("thalesSecurityWorldPath") : null);
    }

    public String[] getThalesHsmAddresses() {
        return thalesHsmAddresses != null ? thalesHsmAddresses :
            (additionalProperties != null ? (String[]) additionalProperties.get("thalesHsmAddresses") : null);
    }

    public String getThalesOperatorCardSet() {
        return thalesOperatorCardSet != null ? thalesOperatorCardSet :
            (additionalProperties != null ? (String) additionalProperties.get("thalesOperatorCardSet") : null);
    }

    public String getThalesAdministratorCardSet() {
        return thalesAdministratorCardSet != null ? thalesAdministratorCardSet :
            (additionalProperties != null ? (String) additionalProperties.get("thalesAdministratorCardSet") : null);
    }
}