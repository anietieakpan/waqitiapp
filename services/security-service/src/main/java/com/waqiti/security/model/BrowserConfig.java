package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Browser Configuration
 * Contains browser and client configuration details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrowserConfig {

    private String browserName;
    private String browserVersion;
    private String osName;
    private String osVersion;
    private String screenResolution;
    private String timezone;
    private String language;
    private Boolean cookiesEnabled;
    private Boolean javaScriptEnabled;
    private List<String> pluginsInstalled;
    private String colorDepth;
    private String platform;
}
