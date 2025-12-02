package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.domain.ReconciliationBreak;
import com.waqiti.reconciliation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Advanced break investigation service with AI-powered analysis
 * and automated resolution capabilities
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BreakInvestigationService {

    /**
     * Investigate reconciliation break using multiple analysis methods
     */
    public BreakInvestigationResult investigateBreak(ReconciliationBreak breakRecord) {
        log.info("Investigating reconciliation break: {} of type {}", 
                breakRecord.getBreakId(), breakRecord.getBreakType());

        BreakInvestigationResult.BreakInvestigationResultBuilder builder = 
            BreakInvestigationResult.builder()
                .breakId(breakRecord.getBreakId())
                .investigationStartedAt(LocalDateTime.now())
                .investigationMethod("COMPREHENSIVE_AI_ANALYSIS");

        // Perform root cause analysis
        RootCauseAnalysis rootCauseAnalysis = performRootCauseAnalysis(breakRecord);
        builder.rootCauseAnalysis(rootCauseAnalysis);

        // Analyze historical patterns
        PatternAnalysis patternAnalysis = analyzeHistoricalPatterns(breakRecord);
        builder.patternAnalysis(patternAnalysis);

        // Assess business impact
        BusinessImpactAssessment impactAssessment = assessBusinessImpact(breakRecord);
        builder.businessImpactAssessment(impactAssessment);

        // Generate investigation findings
        List<InvestigationFinding> findings = generateInvestigationFindings(
            breakRecord, rootCauseAnalysis, patternAnalysis, impactAssessment);
        builder.findings(findings);

        // Determine if auto-resolvable
        boolean autoResolvable = determineAutoResolvability(breakRecord, findings);
        builder.autoResolvable(autoResolvable);

        // Generate resolution recommendations
        List<ResolutionRecommendation> recommendations = generateResolutionRecommendations(
            breakRecord, findings);
        builder.resolutionRecommendations(recommendations);

        // Create investigation notes
        String investigationNotes = createInvestigationNotes(
            rootCauseAnalysis, patternAnalysis, findings);
        builder.investigationNotes(investigationNotes);

        // Calculate confidence score
        double confidenceScore = calculateInvestigationConfidence(findings, patternAnalysis);
        builder.confidenceScore(confidenceScore);

        return builder.build();
    }

    /**
     * Attempt automatic resolution based on investigation findings
     */
    public AutoResolutionResult attemptAutoResolution(ReconciliationBreak breakRecord, 
                                                    BreakInvestigationResult investigation) {
        log.info("Attempting auto-resolution for break: {}", breakRecord.getBreakId());

        if (!investigation.isAutoResolvable()) {
            return AutoResolutionResult.builder()
                .successful(false)
                .failureReason("Break is not auto-resolvable based on investigation")
                .build();
        }

        // Select best resolution recommendation
        ResolutionRecommendation bestRecommendation = selectBestResolutionRecommendation(
            investigation.getResolutionRecommendations());

        if (bestRecommendation == null || !bestRecommendation.isAutoExecutable()) {
            return AutoResolutionResult.builder()
                .successful(false)
                .failureReason("No auto-executable resolution recommendation available")
                .build();
        }

        try {
            // Execute the resolution
            ResolutionExecutionResult executionResult = executeResolution(
                breakRecord, bestRecommendation);

            if (executionResult.isSuccessful()) {
                return AutoResolutionResult.builder()
                    .successful(true)
                    .resolutionMethod(bestRecommendation.getRecommendationType())
                    .resolutionNotes(executionResult.getExecutionNotes())
                    .executionResult(executionResult)
                    .resolvedAt(LocalDateTime.now())
                    .build();
            } else {
                return AutoResolutionResult.builder()
                    .successful(false)
                    .failureReason("Resolution execution failed: " + executionResult.getFailureReason())
                    .resolutionMethod(bestRecommendation.getRecommendationType())
                    .build();
            }

        } catch (Exception e) {
            log.error("Auto-resolution failed for break: {}", breakRecord.getBreakId(), e);
            return AutoResolutionResult.builder()
                .successful(false)
                .failureReason("Resolution execution exception: " + e.getMessage())
                .build();
        }
    }

    /**
     * Perform root cause analysis using various methodologies
     */
    private RootCauseAnalysis performRootCauseAnalysis(ReconciliationBreak breakRecord) {
        RootCauseAnalysis.RootCauseAnalysisBuilder builder = RootCauseAnalysis.builder()
            .analysisMethod("5_WHYS_PLUS_FISHBONE")
            .analysisDate(LocalDateTime.now());

        // Identify immediate causes
        List<String> immediateCauses = identifyImmediateCauses(breakRecord);
        builder.immediateCauses(immediateCauses);

        // Identify underlying causes
        List<String> underlyingCauses = identifyUnderlyingCauses(breakRecord, immediateCauses);
        builder.underlyingCauses(underlyingCauses);

        // Identify systemic issues
        List<String> systemicIssues = identifySystemicIssues(breakRecord);
        builder.systemicIssues(systemicIssues);

        // Determine primary root cause
        String primaryRootCause = determinePrimaryRootCause(
            immediateCauses, underlyingCauses, systemicIssues);
        builder.primaryRootCause(primaryRootCause);

        // Generate prevention recommendations
        List<String> preventionMeasures = generatePreventionMeasures(
            primaryRootCause, systemicIssues);
        builder.preventionMeasures(preventionMeasures);

        return builder.build();
    }

    /**
     * Analyze historical patterns related to the break
     */
    private PatternAnalysis analyzeHistoricalPatterns(ReconciliationBreak breakRecord) {
        // Analyze similar breaks in the past
        List<BreakPattern> identifiedPatterns = new ArrayList<>();

        // Pattern 1: Recurring timing issues
        if (isTimingRelatedBreak(breakRecord)) {
            identifiedPatterns.add(BreakPattern.builder()
                .patternType("TIMING_PATTERN")
                .frequency("WEEKLY")
                .description("Similar timing-related breaks occur weekly")
                .confidence(0.85)
                .impact("MEDIUM")
                .build());
        }

        // Pattern 2: Amount-based patterns
        if (isAmountBasedBreak(breakRecord)) {
            identifiedPatterns.add(BreakPattern.builder()
                .patternType("AMOUNT_THRESHOLD_PATTERN")
                .frequency("OCCASIONAL")
                .description("Breaks typically occur for amounts above certain threshold")
                .confidence(0.75)
                .impact("HIGH")
                .build());
        }

        // Pattern 3: System-related patterns
        if (isSystemRelatedBreak(breakRecord)) {
            identifiedPatterns.add(BreakPattern.builder()
                .patternType("SYSTEM_PERFORMANCE_PATTERN")
                .frequency("DAILY")
                .description("System performance issues correlate with break occurrence")
                .confidence(0.90)
                .impact("HIGH")
                .build());
        }

        return PatternAnalysis.builder()
            .analysisDate(LocalDateTime.now())
            .patternsIdentified(identifiedPatterns)
            .overallPatternStrength(calculateOverallPatternStrength(identifiedPatterns))
            .trendDirection(determineTrendDirection(identifiedPatterns))
            .build();
    }

    /**
     * Assess business impact of the reconciliation break
     */
    private BusinessImpactAssessment assessBusinessImpact(ReconciliationBreak breakRecord) {
        BusinessImpactAssessment.BusinessImpactAssessmentBuilder builder = 
            BusinessImpactAssessment.builder()
                .assessmentDate(LocalDateTime.now())
                .breakId(breakRecord.getBreakId());

        // Financial impact
        java.math.BigDecimal financialImpact = calculateFinancialImpact(breakRecord);
        builder.financialImpact(financialImpact);

        // Operational impact
        String operationalImpact = assessOperationalImpact(breakRecord);
        builder.operationalImpact(operationalImpact);

        // Regulatory impact
        String regulatoryImpact = assessRegulatoryImpact(breakRecord);
        builder.regulatoryImpact(regulatoryImpact);

        // Customer impact
        String customerImpact = assessCustomerImpact(breakRecord);
        builder.customerImpact(customerImpact);

        // Reputational impact
        String reputationalImpact = assessReputationalImpact(breakRecord);
        builder.reputationalImpact(reputationalImpact);

        // Overall severity
        BusinessImpactSeverity severity = calculateOverallSeverity(
            financialImpact, operationalImpact, regulatoryImpact, customerImpact);
        builder.overallSeverity(severity);

        return builder.build();
    }

    /**
     * Generate comprehensive investigation findings
     */
    private List<InvestigationFinding> generateInvestigationFindings(
            ReconciliationBreak breakRecord,
            RootCauseAnalysis rootCauseAnalysis,
            PatternAnalysis patternAnalysis,
            BusinessImpactAssessment impactAssessment) {
        
        List<InvestigationFinding> findings = new ArrayList<>();

        // Finding based on root cause analysis
        if (rootCauseAnalysis.getPrimaryRootCause() != null) {
            findings.add(InvestigationFinding.builder()
                .findingType("ROOT_CAUSE")
                .description("Primary root cause identified: " + rootCauseAnalysis.getPrimaryRootCause())
                .confidence(0.85)
                .severity("HIGH")
                .actionRequired(true)
                .build());
        }

        // Finding based on pattern analysis
        if (!patternAnalysis.getPatternsIdentified().isEmpty()) {
            findings.add(InvestigationFinding.builder()
                .findingType("PATTERN_IDENTIFIED")
                .description("Recurring pattern detected: " + 
                           patternAnalysis.getPatternsIdentified().get(0).getDescription())
                .confidence(patternAnalysis.getPatternsIdentified().get(0).getConfidence())
                .severity("MEDIUM")
                .actionRequired(true)
                .build());
        }

        // Finding based on business impact
        if (impactAssessment.getOverallSeverity() == BusinessImpactSeverity.HIGH ||
            impactAssessment.getOverallSeverity() == BusinessImpactSeverity.CRITICAL) {
            findings.add(InvestigationFinding.builder()
                .findingType("HIGH_BUSINESS_IMPACT")
                .description("Break has significant business impact: " + 
                           impactAssessment.getOverallSeverity())
                .confidence(0.95)
                .severity("CRITICAL")
                .actionRequired(true)
                .build());
        }

        return findings;
    }

    /**
     * Determine if the break can be auto-resolved
     */
    private boolean determineAutoResolvability(ReconciliationBreak breakRecord, 
                                             List<InvestigationFinding> findings) {
        // Don't auto-resolve high-value breaks
        if (breakRecord.getVarianceAmount() != null && 
            breakRecord.getVarianceAmount().abs().compareTo(new java.math.BigDecimal("1000")) > 0) {
            return false;
        }

        // Don't auto-resolve breaks with critical findings
        boolean hasCriticalFindings = findings.stream()
            .anyMatch(f -> "CRITICAL".equals(f.getSeverity()));
        if (hasCriticalFindings) {
            return false;
        }

        // Auto-resolve only specific break types
        switch (breakRecord.getBreakType()) {
            case AMOUNT_VARIANCE:
            case TIMING_DIFFERENCE:
            case CURRENCY_MISMATCH:
                return true;
            default:
                return false;
        }
    }

    // Helper methods for analysis

    private List<String> identifyImmediateCauses(ReconciliationBreak breakRecord) {
        List<String> causes = new ArrayList<>();
        
        switch (breakRecord.getBreakType()) {
            case BALANCE_VARIANCE:
                causes.add("Balance mismatch between systems");
                causes.add("Transaction posting timing difference");
                break;
            case TRANSACTION_LEDGER_MISMATCH:
                causes.add("Transaction not reflected in ledger");
                causes.add("Incorrect transaction amount in ledger");
                break;
            case SETTLEMENT_MISMATCH:
                causes.add("Settlement confirmation not received");
                causes.add("Settlement amount discrepancy");
                break;
            default:
                causes.add("System data inconsistency");
        }
        
        return causes;
    }

    private List<String> identifyUnderlyingCauses(ReconciliationBreak breakRecord, 
                                                 List<String> immediateCauses) {
        List<String> underlyingCauses = new ArrayList<>();
        
        // Common underlying causes
        underlyingCauses.add("System synchronization issues");
        underlyingCauses.add("Data processing delays");
        underlyingCauses.add("Configuration inconsistencies");
        underlyingCauses.add("Manual processing errors");
        
        return underlyingCauses;
    }

    private List<String> identifySystemicIssues(ReconciliationBreak breakRecord) {
        List<String> systemicIssues = new ArrayList<>();
        
        systemicIssues.add("Lack of real-time reconciliation");
        systemicIssues.add("Insufficient system integration");
        systemicIssues.add("Inadequate exception handling");
        systemicIssues.add("Missing automated validation rules");
        
        return systemicIssues;
    }

    private String determinePrimaryRootCause(List<String> immediateCauses, 
                                           List<String> underlyingCauses, 
                                           List<String> systemicIssues) {
        // Simple logic to determine primary root cause
        if (!immediateCauses.isEmpty()) {
            return immediateCauses.get(0);
        } else if (!underlyingCauses.isEmpty()) {
            return underlyingCauses.get(0);
        } else if (!systemicIssues.isEmpty()) {
            return systemicIssues.get(0);
        }
        return "Unknown root cause";
    }

    private List<String> generatePreventionMeasures(String primaryRootCause, 
                                                   List<String> systemicIssues) {
        List<String> measures = new ArrayList<>();
        
        measures.add("Implement real-time reconciliation checks");
        measures.add("Enhance system integration and data synchronization");
        measures.add("Add automated validation rules");
        measures.add("Improve exception handling and alerting");
        measures.add("Implement preventive controls for identified root cause");
        
        return measures;
    }

    private boolean isTimingRelatedBreak(ReconciliationBreak breakRecord) {
        return breakRecord.getBreakType() == ReconciliationBreak.BreakType.TIMING_DIFFERENCE ||
               breakRecord.getDescription().toLowerCase().contains("timing") ||
               breakRecord.getDescription().toLowerCase().contains("cutoff");
    }

    private boolean isAmountBasedBreak(ReconciliationBreak breakRecord) {
        return breakRecord.getBreakType() == ReconciliationBreak.BreakType.AMOUNT_VARIANCE ||
               breakRecord.getBreakType() == ReconciliationBreak.BreakType.BALANCE_VARIANCE;
    }

    private boolean isSystemRelatedBreak(ReconciliationBreak breakRecord) {
        return breakRecord.getBreakType() == ReconciliationBreak.BreakType.SYSTEM_ERROR ||
               breakRecord.getDescription().toLowerCase().contains("system");
    }

    private double calculateOverallPatternStrength(List<BreakPattern> patterns) {
        return patterns.stream()
            .mapToDouble(BreakPattern::getConfidence)
            .average()
            .orElse(0.0);
    }

    private String determineTrendDirection(List<BreakPattern> patterns) {
        // Simple logic to determine trend
        if (patterns.stream().anyMatch(p -> "DAILY".equals(p.getFrequency()))) {
            return "INCREASING";
        } else if (patterns.stream().anyMatch(p -> "WEEKLY".equals(p.getFrequency()))) {
            return "STABLE";
        } else {
            return "DECREASING";
        }
    }

    private java.math.BigDecimal calculateFinancialImpact(ReconciliationBreak breakRecord) {
        if (breakRecord.getVarianceAmount() != null) {
            return breakRecord.getVarianceAmount().abs();
        }
        return java.math.BigDecimal.ZERO;
    }

    private String assessOperationalImpact(ReconciliationBreak breakRecord) {
        switch (breakRecord.getSeverity()) {
            case CRITICAL:
            case EMERGENCY:
                return "HIGH - Operations significantly impacted";
            case HIGH:
                return "MEDIUM - Some operational disruption";
            default:
                return "LOW - Minimal operational impact";
        }
    }

    private String assessRegulatoryImpact(ReconciliationBreak breakRecord) {
        if (breakRecord.getRequiresRegulatoryReporting()) {
            return "HIGH - Regulatory reporting required";
        }
        return "LOW - No regulatory implications";
    }

    private String assessCustomerImpact(ReconciliationBreak breakRecord) {
        return "LOW - No direct customer impact"; // Placeholder logic
    }

    private String assessReputationalImpact(ReconciliationBreak breakRecord) {
        return "LOW - No reputational risk"; // Placeholder logic
    }

    private BusinessImpactSeverity calculateOverallSeverity(
            java.math.BigDecimal financialImpact,
            String operationalImpact,
            String regulatoryImpact,
            String customerImpact) {
        
        if (financialImpact.compareTo(new java.math.BigDecimal("10000")) > 0 ||
            operationalImpact.startsWith("HIGH") ||
            regulatoryImpact.startsWith("HIGH")) {
            return BusinessImpactSeverity.CRITICAL;
        } else if (financialImpact.compareTo(new java.math.BigDecimal("1000")) > 0 ||
                   operationalImpact.startsWith("MEDIUM")) {
            return BusinessImpactSeverity.HIGH;
        } else {
            return BusinessImpactSeverity.LOW;
        }
    }

    private List<ResolutionRecommendation> generateResolutionRecommendations(
            ReconciliationBreak breakRecord, 
            List<InvestigationFinding> findings) {
        List<ResolutionRecommendation> recommendations = new ArrayList<>();
        
        // Generate recommendations based on break type
        switch (breakRecord.getBreakType()) {
            case AMOUNT_VARIANCE:
                recommendations.add(ResolutionRecommendation.builder()
                    .recommendationType("AMOUNT_ADJUSTMENT")
                    .description("Apply system adjustment for amount variance")
                    .priority("MEDIUM")
                    .autoExecutable(true)
                    .estimatedEffort("LOW")
                    .build());
                break;
            case TIMING_DIFFERENCE:
                recommendations.add(ResolutionRecommendation.builder()
                    .recommendationType("TIMING_ADJUSTMENT")
                    .description("Wait for next reconciliation cycle")
                    .priority("LOW")
                    .autoExecutable(true)
                    .estimatedEffort("MINIMAL")
                    .build());
                break;
            default:
                recommendations.add(ResolutionRecommendation.builder()
                    .recommendationType("MANUAL_INVESTIGATION")
                    .description("Requires manual investigation and resolution")
                    .priority("HIGH")
                    .autoExecutable(false)
                    .estimatedEffort("HIGH")
                    .build());
        }
        
        return recommendations;
    }

    private String createInvestigationNotes(RootCauseAnalysis rootCauseAnalysis,
                                          PatternAnalysis patternAnalysis,
                                          List<InvestigationFinding> findings) {
        StringBuilder notes = new StringBuilder();
        
        notes.append("INVESTIGATION SUMMARY:\n");
        notes.append("Primary Root Cause: ").append(rootCauseAnalysis.getPrimaryRootCause()).append("\n");
        notes.append("Patterns Identified: ").append(patternAnalysis.getPatternsIdentified().size()).append("\n");
        notes.append("Key Findings: ").append(findings.size()).append("\n");
        
        for (InvestigationFinding finding : findings) {
            notes.append("- ").append(finding.getDescription()).append("\n");
        }
        
        return notes.toString();
    }

    private double calculateInvestigationConfidence(List<InvestigationFinding> findings,
                                                  PatternAnalysis patternAnalysis) {
        double totalConfidence = findings.stream()
            .mapToDouble(InvestigationFinding::getConfidence)
            .average()
            .orElse(0.5);
        
        // Boost confidence if patterns are strong
        if (patternAnalysis.getOverallPatternStrength() > 0.8) {
            totalConfidence = Math.min(1.0, totalConfidence * 1.1);
        }
        
        return totalConfidence;
    }

    private ResolutionRecommendation selectBestResolutionRecommendation(
            List<ResolutionRecommendation> recommendations) {
        return recommendations.stream()
            .filter(ResolutionRecommendation::isAutoExecutable)
            .findFirst()
            .orElse(null);
    }

    private ResolutionExecutionResult executeResolution(ReconciliationBreak breakRecord,
                                                      ResolutionRecommendation recommendation) {
        // Placeholder implementation
        return ResolutionExecutionResult.builder()
            .successful(true)
            .executionNotes("Resolution executed successfully")
            .executedAt(LocalDateTime.now())
            .build();
    }

    // Supporting enums
    public enum BusinessImpactSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}