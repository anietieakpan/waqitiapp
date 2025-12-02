package com.waqiti.user.service;

import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class SanctionsScreeningResult {
    private boolean sanctionsMatch;
    private double riskScore;
    private int matchesFound;
    private List<String> sanctionedLists;
    private String provider;
    private boolean requiresImmediateAction;
    private String complianceAction;
}
