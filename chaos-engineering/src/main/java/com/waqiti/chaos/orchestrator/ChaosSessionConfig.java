package com.waqiti.chaos.orchestrator;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChaosSessionConfig {
    private boolean runValidation;
    private boolean allowParallel;
    private boolean includeResilienceTests;
    private boolean generateReport;
    private int maxExperiments;
    private String targetEnvironment;
    private String notificationEmail;
    
    public static ChaosSessionConfig defaultConfig() {
        return ChaosSessionConfig.builder()
            .runValidation(true)
            .allowParallel(false)
            .includeResilienceTests(true)
            .generateReport(true)
            .maxExperiments(10)
            .targetEnvironment("staging")
            .build();
    }
    
    public static ChaosSessionConfig minimalConfig() {
        return ChaosSessionConfig.builder()
            .runValidation(true)
            .allowParallel(false)
            .includeResilienceTests(false)
            .generateReport(false)
            .maxExperiments(3)
            .build();
    }
    
    public static ChaosSessionConfig aggressiveConfig() {
        return ChaosSessionConfig.builder()
            .runValidation(false)
            .allowParallel(true)
            .includeResilienceTests(true)
            .generateReport(true)
            .maxExperiments(Integer.MAX_VALUE)
            .build();
    }
}