package com.waqiti.common.fraud.mapper;

import com.waqiti.common.fraud.dto.*;
import com.waqiti.common.fraud.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between DTO and Model classes at API boundaries.
 *
 * Architecture:
 * - DTOs: Used in Controllers for REST API requests/responses
 * - Models: Used in Services for business logic
 *
 * This mapper sits at the boundary between Controller and Service layers.
 */
@Component
public class FraudMapper {

    // ========================
    // FraudScore Mapping
    // ========================

    public com.waqiti.common.fraud.model.FraudScore toModel(com.waqiti.common.fraud.dto.FraudScore dto) {
        if (dto == null) return null;

        return com.waqiti.common.fraud.model.FraudScore.builder()
                .score(dto.getScore())
                .overallScore(dto.getScore())
                .confidence(dto.getConfidence())
                .confidenceLevel(dto.getConfidence())
                .calculatedAt(dto.getScoringTimestamp())
                .scoringVersion(dto.getModelVersion())
                .build();
    }

    public com.waqiti.common.fraud.dto.FraudScore toDto(com.waqiti.common.fraud.model.FraudScore model) {
        if (model == null) return null;

        return com.waqiti.common.fraud.dto.FraudScore.builder()
                .score(model.getScore())
                .confidence(model.getConfidence())
                .scoringTimestamp(model.getCalculatedAt())
                .modelVersion(model.getScoringVersion())
                .build();
    }

    // ========================
    // FraudRiskLevel Mapping
    // ========================

    public com.waqiti.common.fraud.model.FraudRiskLevel toModel(com.waqiti.common.fraud.dto.FraudRiskLevel dto) {
        if (dto == null) return com.waqiti.common.fraud.model.FraudRiskLevel.UNKNOWN;
        return com.waqiti.common.fraud.model.FraudRiskLevel.fromDto(dto);
    }

    public com.waqiti.common.fraud.dto.FraudRiskLevel toDto(com.waqiti.common.fraud.model.FraudRiskLevel model) {
        if (model == null) return com.waqiti.common.fraud.dto.FraudRiskLevel.UNKNOWN;
        return model.toDto();
    }

    // ========================
    // Location Mapping
    // ========================

    public com.waqiti.common.fraud.model.Location toModel(com.waqiti.common.fraud.model.Location dto) {
        if (dto == null) return null;

        return com.waqiti.common.fraud.model.Location.builder()
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .country(dto.getCountry())
                .city(dto.getCity())
                .build();
    }

    // ========================
    // TransactionEvent Mapping
    // ========================

    public com.waqiti.common.fraud.model.TransactionEvent toModel(com.waqiti.common.fraud.dto.TransactionEvent dto) {
        if (dto == null) return null;

        return com.waqiti.common.fraud.model.TransactionEvent.builder()
                .transactionId(dto.getTransactionId())
                .userId(dto.getUserId())
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .timestamp(dto.getTimestamp())
                .merchantId(dto.getMerchantId())
                .merchantName(dto.getMerchantName())
                .merchantCategory(dto.getMerchantCategory())
                .ipAddress(dto.getIpAddress())
                .deviceId(dto.getDeviceId())
                .userAgent(dto.getUserAgent())
                .location(toModel(dto.getLocation()))
                .build();
    }

    public com.waqiti.common.fraud.dto.TransactionEvent toDto(com.waqiti.common.fraud.model.TransactionEvent model) {
        if (model == null) return null;

        return com.waqiti.common.fraud.dto.TransactionEvent.builder()
                .transactionId(model.getTransactionId())
                .userId(model.getUserId())
                .amount(model.getAmount())
                .currency(model.getCurrency())
                .timestamp(model.getTimestamp())
                .merchantId(model.getMerchantId())
                .merchantName(model.getMerchantName())
                .merchantCategory(model.getMerchantCategory())
                .ipAddress(model.getIpAddress())
                .deviceId(model.getDeviceId())
                .userAgent(model.getUserAgent())
                .location(toDto(model.getLocation()))
                .build();
    }

    // ========================
    // UserRiskProfile Mapping
    // ========================

    public com.waqiti.common.fraud.profiling.UserRiskProfile toModel(com.waqiti.common.fraud.profiling.UserRiskProfile dto) {
        if (dto == null) return null;

        return com.waqiti.common.fraud.profiling.UserRiskProfile.builder()
                .userId(dto.getUserId())
                .overallRiskScore(dto.getRiskScore())
                .riskLevel(dto.getRiskLevel()) // Direct assignment - same type
                .accountAge(dto.getAccountAge())
                .build();
    }

    public com.waqiti.common.fraud.profiling.UserRiskProfile toDto(com.waqiti.common.fraud.profiling.UserRiskProfile model) {
        if (model == null) return null;

        return com.waqiti.common.fraud.profiling.UserRiskProfile.builder()
                .userId(model.getUserId())
                .riskScore(model.getOverallRiskScore())
                .riskLevel(model.getRiskLevel()) // Direct assignment - same type
                .accountAge(model.getAccountAge())
                .build();
    }

    // ========================
    // FraudAlert Mapping
    // ========================

    public com.waqiti.common.fraud.model.FraudAlert toModel(com.waqiti.common.fraud.dto.FraudAlert dto) {
        if (dto == null) return null;

        return com.waqiti.common.fraud.model.FraudAlert.builder()
                .id(dto.getAlertId())
                .transactionId(dto.getTransactionId())
                .userId(dto.getUserId())
                .fraudProbability(dto.getFraudScore() != null ?
                    java.math.BigDecimal.valueOf(dto.getFraudScore().getScore()) : null)
                .riskLevel(toFraudAlertRiskLevel(dto.getRiskLevel()))
                .detectedAt(dto.getTimestamp())
                .build();
    }

    public com.waqiti.common.fraud.dto.FraudAlert toDto(com.waqiti.common.fraud.model.FraudAlert model) {
        if (model == null) return null;

        return com.waqiti.common.fraud.dto.FraudAlert.builder()
                .alertId(model.getId())
                .transactionId(model.getTransactionId())
                .userId(model.getUserId())
                .fraudScore(model.getFraudProbability() != null ?
                    com.waqiti.common.fraud.dto.FraudScore.builder()
                        .score(model.getFraudProbability().doubleValue())
                        .build() : null)
                .riskLevel(fromFraudAlertRiskLevel(model.getRiskLevel()))
                .timestamp(model.getDetectedAt())
                .build();
    }

    // ========================
    // FraudAnalysisResult Mapping
    // ========================

    public com.waqiti.common.fraud.dto.FraudAnalysisResult toDto(com.waqiti.common.fraud.FraudAnalysisResult model) {
        if (model == null) return null;

        return com.waqiti.common.fraud.dto.FraudAnalysisResult.builder()
                .transactionId(model.getTransactionId())
                .userId(model.getUserId())
                .fraudScore(toDto(model.getFraudScore()))
                .riskLevel(toDto(model.getRiskLevel()))
                .ruleViolations(toFraudRuleViolationDtoList(model.getRuleViolations()))
                .behavioralAnomalies(toBehavioralAnomalyDtoList(model.getBehavioralAnomalies()))
                .patternMatches(toPatternMatchDtoList(model.getPatternMatches()))
                .analysisTimestamp(model.getAnalysisTimestamp())
                .build();
    }

    // ========================
    // BehavioralAnomaly List Mapping
    // ========================

    private List<com.waqiti.common.fraud.dto.BehavioralAnomaly> toBehavioralAnomalyDtoList(
            List<com.waqiti.common.fraud.model.BehavioralAnomaly> anomalies) {
        if (anomalies == null) return null;
        return anomalies.stream()
                .map(this::toBehavioralAnomalyDto)
                .collect(java.util.stream.Collectors.toList());
    }

    private com.waqiti.common.fraud.dto.BehavioralAnomaly toBehavioralAnomalyDto(
            com.waqiti.common.fraud.model.BehavioralAnomaly model) {
        if (model == null) return null;
        return com.waqiti.common.fraud.dto.BehavioralAnomaly.builder()
                .type(convertAnomalyTypeToDto(model.getType()))
                .severity(convertAnomalySeverityToDto(model.getSeverity()))
                .description(model.getDescription())
                .confidence(model.getConfidence() != null ? model.getConfidence() : 0.0)
                .build();
    }

    /**
     * Convert model AnomalyType to DTO AnomalyType
     */
    private com.waqiti.common.fraud.dto.AnomalyType convertAnomalyTypeToDto(
            com.waqiti.common.fraud.model.AnomalyType modelType) {
        if (modelType == null) return com.waqiti.common.fraud.dto.AnomalyType.UNUSUAL_AMOUNT;

        return switch (modelType) {
            case SPENDING_PATTERN, TRANSACTION_PATTERN -> com.waqiti.common.fraud.dto.AnomalyType.UNUSUAL_AMOUNT;
            case TIME_PATTERN, LOGIN_PATTERN -> com.waqiti.common.fraud.dto.AnomalyType.UNUSUAL_TIME;
            case LOCATION_JUMP, ACCESS_PATTERN -> com.waqiti.common.fraud.dto.AnomalyType.UNUSUAL_LOCATION;
            case SESSION_BEHAVIOR, INTERACTION_SPEED -> com.waqiti.common.fraud.dto.AnomalyType.HIGH_VELOCITY;
            case DEVICE_SWITCHING -> com.waqiti.common.fraud.dto.AnomalyType.NEW_DEVICE;
            case MERCHANT_PATTERN -> com.waqiti.common.fraud.dto.AnomalyType.UNUSUAL_MERCHANT;
            default -> com.waqiti.common.fraud.dto.AnomalyType.UNUSUAL_AMOUNT;
        };
    }

    /**
     * Convert String severity to DTO AnomalySeverity enum
     */
    private com.waqiti.common.fraud.dto.AnomalySeverity convertAnomalySeverityToDto(String severity) {
        if (severity == null) return com.waqiti.common.fraud.dto.AnomalySeverity.LOW;

        return switch (severity.toUpperCase()) {
            case "HIGH", "CRITICAL" -> com.waqiti.common.fraud.dto.AnomalySeverity.HIGH;
            case "MEDIUM" -> com.waqiti.common.fraud.dto.AnomalySeverity.MEDIUM;
            default -> com.waqiti.common.fraud.dto.AnomalySeverity.LOW;
        };
    }

    // ========================
    // PatternMatch List Mapping
    // ========================

    private List<com.waqiti.common.fraud.dto.PatternMatch> toPatternMatchDtoList(
            List<com.waqiti.common.fraud.model.PatternMatch> matches) {
        if (matches == null) return null;
        return matches.stream()
                .map(this::toPatternMatchDto)
                .collect(java.util.stream.Collectors.toList());
    }

    private com.waqiti.common.fraud.dto.PatternMatch toPatternMatchDto(
            com.waqiti.common.fraud.model.PatternMatch model) {
        if (model == null) return null;
        return com.waqiti.common.fraud.dto.PatternMatch.builder()
                .patternType(convertPatternTypeToDto(model.getPatternType()))
                .confidence(model.getConfidenceLevel() != null ? model.getConfidenceLevel().doubleValue() : 0.0)
                .description(model.getDescription())
                .build();
    }

    /**
     * Convert String pattern type to DTO FraudPatternType enum
     */
    private com.waqiti.common.fraud.dto.FraudPatternType convertPatternTypeToDto(String patternType) {
        if (patternType == null) return com.waqiti.common.fraud.dto.FraudPatternType.VELOCITY_ATTACK;

        String upper = patternType.toUpperCase();
        if (upper.contains("CARD") || upper.contains("TEST")) {
            return com.waqiti.common.fraud.dto.FraudPatternType.CARD_TESTING;
        } else if (upper.contains("TAKEOVER") || upper.contains("ACCOUNT")) {
            return com.waqiti.common.fraud.dto.FraudPatternType.ACCOUNT_TAKEOVER;
        } else if (upper.contains("LAUNDER") || upper.contains("MONEY")) {
            return com.waqiti.common.fraud.dto.FraudPatternType.MONEY_LAUNDERING;
        } else if (upper.contains("SYNTHETIC") || upper.contains("IDENTITY")) {
            return com.waqiti.common.fraud.dto.FraudPatternType.SYNTHETIC_IDENTITY;
        } else if (upper.contains("VELOCITY") || upper.contains("RATE")) {
            return com.waqiti.common.fraud.dto.FraudPatternType.VELOCITY_ATTACK;
        } else if (upper.contains("CREDENTIAL") || upper.contains("STUFF")) {
            return com.waqiti.common.fraud.dto.FraudPatternType.CREDENTIAL_STUFFING;
        }
        return com.waqiti.common.fraud.dto.FraudPatternType.VELOCITY_ATTACK;
    }

    // ========================
    // FraudMonitoringStatistics Mapping
    // ========================

    public com.waqiti.common.fraud.dto.FraudMonitoringStatistics toDto(
            com.waqiti.common.fraud.model.FraudMonitoringStatistics model) {
        if (model == null) return null;

        return com.waqiti.common.fraud.dto.FraudMonitoringStatistics.builder()
                .totalTransactionsAnalyzed(model.getTotalTransactionsAnalyzed())
                .fraudDetected(model.getFraudDetected())
                .falsePositives(model.getFalsePositives())
                .truePositives(model.getTruePositives())
                .averageFraudScore(model.getAverageFraudScore())
                .averageProcessingTime(model.getAverageProcessingTime())
                .highRiskTransactions(model.getHighRiskTransactions())
                .blockedTransactions(model.getBlockedTransactions())
                .detectionRate(model.getDetectionRate())
                .precision(model.getPrecision())
                .recall(model.getRecall())
                .f1Score(model.getF1Score())
                .falsePositiveRate(model.getFalsePositiveRate())
                .build();
    }

    // ========================
    // List Conversion Helpers
    // ========================

    public <D, M> List<M> toModelList(List<D> dtoList, java.util.function.Function<D, M> converter) {
        if (dtoList == null) return null;
        return dtoList.stream().map(converter).collect(Collectors.toList());
    }

    public <M, D> List<D> toDtoList(List<M> modelList, java.util.function.Function<M, D> converter) {
        if (modelList == null) return null;
        return modelList.stream().map(converter).collect(Collectors.toList());
    }

    // ========================
    // Location Mapping
    // ========================

    public com.waqiti.common.fraud.model.Location toModel(com.waqiti.common.fraud.dto.Location dto) {
        if (dto == null) return null;
        return com.waqiti.common.fraud.model.Location.builder()
                .country(dto.getCountry())
                .city(dto.getCity())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .build();
    }

    public com.waqiti.common.fraud.dto.Location toDto(com.waqiti.common.fraud.model.Location model) {
        if (model == null) return null;
        return com.waqiti.common.fraud.dto.Location.builder()
                .country(model.getCountry())
                .city(model.getCity())
                .latitude(model.getLatitude())
                .longitude(model.getLongitude())
                .build();
    }

    // ========================
    // RiskLevel Mapping
    // ========================

    public com.waqiti.common.enums.RiskLevel toModel(com.waqiti.common.fraud.profiling.UserRiskProfileService.RiskLevel riskLevel) {
        if (riskLevel == null) return null;
        return switch (riskLevel) {
            case MINIMAL -> com.waqiti.common.enums.RiskLevel.MINIMAL;
            case LOW -> com.waqiti.common.enums.RiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.enums.RiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.enums.RiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.enums.RiskLevel.CRITICAL;
        };
    }

    public com.waqiti.common.fraud.dto.FraudRiskLevel toDto(com.waqiti.common.fraud.profiling.UserRiskProfileService.RiskLevel riskLevel) {
        if (riskLevel == null) return null;
        return switch (riskLevel) {
            case MINIMAL -> com.waqiti.common.fraud.dto.FraudRiskLevel.MINIMAL;
            case LOW -> com.waqiti.common.fraud.dto.FraudRiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.fraud.dto.FraudRiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.dto.FraudRiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.dto.FraudRiskLevel.CRITICAL;
        };
    }

    // Additional RiskLevel conversions for package consolidation
    public com.waqiti.common.fraud.profiling.UserRiskProfileService.RiskLevel toModel(com.waqiti.common.enums.RiskLevel riskLevel) {
        if (riskLevel == null) return null;
        return switch (riskLevel) {
            case MINIMAL -> com.waqiti.common.fraud.profiling.UserRiskProfileService.RiskLevel.MINIMAL;
            case LOW -> com.waqiti.common.fraud.profiling.UserRiskProfileService.RiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.fraud.profiling.UserRiskProfileService.RiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.profiling.UserRiskProfileService.RiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.profiling.UserRiskProfileService.RiskLevel.CRITICAL;
        };
    }

    public com.waqiti.common.fraud.model.RiskLevel toModelRiskLevel(com.waqiti.common.fraud.model.FraudRiskLevel fraudRiskLevel) {
        if (fraudRiskLevel == null) return null;
        return switch (fraudRiskLevel) {
            case LOW -> com.waqiti.common.fraud.model.RiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.fraud.model.RiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.model.RiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.model.RiskLevel.CRITICAL;
        };
    }

    public com.waqiti.common.fraud.model.FraudRiskLevel toFraudRiskLevel(com.waqiti.common.fraud.model.RiskLevel riskLevel) {
        if (riskLevel == null) return null;
        return switch (riskLevel) {
            case MINIMAL, LOW -> com.waqiti.common.fraud.model.FraudRiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.fraud.model.FraudRiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.model.FraudRiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.model.FraudRiskLevel.CRITICAL;
        };
    }

    // ========================
    // FraudAlert RiskLevel Conversion
    // ========================

    /**
     * Convert DTO FraudRiskLevel to Model RiskLevel for FraudAlert
     */
    public com.waqiti.common.fraud.model.RiskLevel toFraudAlertRiskLevel(com.waqiti.common.fraud.dto.FraudRiskLevel fraudRiskLevel) {
        if (fraudRiskLevel == null) return null;
        return switch (fraudRiskLevel) {
            case LOW -> com.waqiti.common.fraud.model.RiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.fraud.model.RiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.model.RiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.model.RiskLevel.CRITICAL;
            case UNKNOWN -> com.waqiti.common.fraud.model.RiskLevel.MINIMAL;
        };
    }

    /**
     * Convert Model RiskLevel to DTO FraudRiskLevel for FraudAlert
     */
    public com.waqiti.common.fraud.dto.FraudRiskLevel fromFraudAlertRiskLevel(com.waqiti.common.fraud.model.RiskLevel riskLevel) {
        if (riskLevel == null) return null;
        return switch (riskLevel) {
            case MINIMAL, LOW -> com.waqiti.common.fraud.dto.FraudRiskLevel.LOW;
            case MEDIUM -> com.waqiti.common.fraud.dto.FraudRiskLevel.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.dto.FraudRiskLevel.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.dto.FraudRiskLevel.CRITICAL;
        };
    }

    // ========================
    // FraudRuleViolation List Mapping
    // ========================

    public List<com.waqiti.common.fraud.dto.FraudRuleViolation> toFraudRuleViolationDtoList(
            List<com.waqiti.common.fraud.rules.FraudRuleViolation> violations) {
        if (violations == null) return null;
        return violations.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public com.waqiti.common.fraud.dto.FraudRuleViolation toDto(com.waqiti.common.fraud.rules.FraudRuleViolation violation) {
        if (violation == null) return null;
        return com.waqiti.common.fraud.dto.FraudRuleViolation.builder()
                .ruleId(violation.getRuleId())
                .ruleName(violation.getRuleName())
                .severity(convertViolationSeverityToDto(violation.getSeverity()))
                .message(violation.getMessage())
                .build();
    }

    /**
     * Convert ViolationSeverity to RuleViolationSeverity
     */
    private com.waqiti.common.fraud.dto.RuleViolationSeverity convertViolationSeverityToDto(
            com.waqiti.common.fraud.rules.FraudRuleViolation.ViolationSeverity severity) {
        if (severity == null) return com.waqiti.common.fraud.dto.RuleViolationSeverity.LOW;
        return switch (severity) {
            case LOW -> com.waqiti.common.fraud.dto.RuleViolationSeverity.LOW;
            case MEDIUM -> com.waqiti.common.fraud.dto.RuleViolationSeverity.MEDIUM;
            case HIGH -> com.waqiti.common.fraud.dto.RuleViolationSeverity.HIGH;
            case CRITICAL -> com.waqiti.common.fraud.dto.RuleViolationSeverity.CRITICAL;
        };
    }
}
