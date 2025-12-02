package com.waqiti.common.security.hsm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * HSM Configuration Properties
 */
@Data
@ConfigurationProperties(prefix = "waqiti.security.hsm")
public class HSMProperties {
    
    private boolean enabled = false;
    private String provider = "pkcs11";
    private boolean primaryMode = false;
    private boolean fallbackToVault = true;
    private String keyPrefix = "waqiti-hsm";
    
    private Pkcs11Config pkcs11 = new Pkcs11Config();
    private AwsCloudHsmConfig awsCloudHsm = new AwsCloudHsmConfig();
    
    @Data
    public static class Pkcs11Config {
        private String configName = "WaqitiHSM";
        private String libraryPath = "/opt/cloudhsm/lib/libcloudhsm_pkcs11.so";
        private int slotId = 0;
        private String pin;
        private boolean enableFips = true;
        private boolean enableAuditLogging = true;
        private int sessionTimeout = 300; // 5 minutes
        private int maxRetries = 3;
        private Map<String, Object> additionalProperties;
    }
    
    @Data
    public static class AwsCloudHsmConfig {
        private String region = "us-east-1";
        private String clusterId;
        private String userName;
        private String password;
        private boolean enableLogging = true;
        private int connectionTimeout = 30;
        private Map<String, Object> additionalProperties;
    }
}