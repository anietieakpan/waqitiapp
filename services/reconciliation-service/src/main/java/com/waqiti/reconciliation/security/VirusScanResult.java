package com.waqiti.reconciliation.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of virus scanning operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirusScanResult {
    private boolean isClean;
    private List<String> threats;
    private long scanDuration;
    private List<String> scanEngines;
}