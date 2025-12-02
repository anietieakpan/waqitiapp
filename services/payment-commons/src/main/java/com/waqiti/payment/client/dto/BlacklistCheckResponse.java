package com.waqiti.payment.client.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blacklist check response DTO
 * Response from blacklist verification checks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BlacklistCheckResponse {
    
    private UUID checkId;
    
    private UUID requestId;
    
    private String entityId;
    
    private String entityType; // USER, MERCHANT, IP, DEVICE, EMAIL, PHONE
    
    private CheckStatus status;
    
    private LocalDateTime checkedAt;
    
    private Long checkDurationMs;
    
    // Core results
    private boolean isBlacklisted;
    
    private BlacklistMatchSeverity severity;
    
    @Builder.Default
    private List<BlacklistMatch> matches = List.of();
    
    // Check details
    private CheckDetails checkDetails;
    
    // Additional context
    private Map<String, Object> additionalData;
    
    public enum CheckStatus {
        SUCCESS,
        PARTIAL,
        FAILED,
        TIMEOUT,
        ERROR
    }
    
    public enum BlacklistMatchSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlacklistMatch {
        private UUID matchId;
        
        private String blacklistName;
        
        private String blacklistType; // GLOBAL, INTERNAL, REGULATORY, INDUSTRY
        
        private String matchType; // EXACT, PARTIAL, FUZZY, PATTERN
        
        private String matchedValue;
        
        private String originalValue;
        
        private Double matchConfidence; // 0.0-1.0
        
        private MatchSeverity severity;
        
        private String reason;
        
        private LocalDateTime blacklistedAt;
        
        private LocalDateTime expiresAt;
        
        private String blacklistedBy;
        
        private String category; // FRAUD, SANCTIONS, INTERNAL_POLICY, etc.
        
        private boolean isActive;
        
        private Map<String, Object> matchMetadata;
        
        public enum MatchSeverity {
            INFO,
            WARNING,
            BLOCK,
            CRITICAL
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckDetails {
        @Builder.Default
        private List<String> blacklistsChecked = List.of();
        
        private Integer totalBlacklistsChecked;
        
        private Integer successfulChecks;
        
        private Integer failedChecks;
        
        @Builder.Default
        private List<String> failedBlacklists = List.of();
        
        private String checkMethod; // API, DATABASE, CACHE
        
        private boolean usedCache;
        
        private LocalDateTime cacheExpiry;
        
        private Map<String, Object> checkConfiguration;
    }
    
    // Business logic methods
    public boolean hasMatches() {
        return matches != null && !matches.isEmpty();
    }
    
    public boolean hasActiveMatches() {
        return matches != null && 
               matches.stream().anyMatch(BlacklistMatch::isActive);
    }
    
    public boolean hasHighSeverityMatches() {
        return matches != null && 
               matches.stream()
                   .anyMatch(match -> match.getSeverity() == BlacklistMatch.MatchSeverity.CRITICAL ||
                                    match.getSeverity() == BlacklistMatch.MatchSeverity.BLOCK);
    }
    
    public boolean hasBlockingMatches() {
        return matches != null && 
               matches.stream()
                   .anyMatch(match -> match.getSeverity() == BlacklistMatch.MatchSeverity.BLOCK ||
                                    match.getSeverity() == BlacklistMatch.MatchSeverity.CRITICAL);
    }
    
    public boolean isFullySuccessful() {
        return status == CheckStatus.SUCCESS;
    }
    
    public boolean hasRegulatoryMatches() {
        return matches != null && 
               matches.stream()
                   .anyMatch(match -> "REGULATORY".equals(match.getBlacklistType()) ||
                                    "SANCTIONS".equals(match.getCategory()));
    }
    
    public boolean hasFraudMatches() {
        return matches != null && 
               matches.stream()
                   .anyMatch(match -> "FRAUD".equals(match.getCategory()));
    }
    
    public List<BlacklistMatch> getActiveMatches() {
        if (matches == null) {
            return List.of();
        }
        return matches.stream()
            .filter(BlacklistMatch::isActive)
            .toList();
    }
    
    public List<BlacklistMatch> getHighSeverityMatches() {
        if (matches == null) {
            return List.of();
        }
        return matches.stream()
            .filter(match -> match.getSeverity() == BlacklistMatch.MatchSeverity.CRITICAL ||
                           match.getSeverity() == BlacklistMatch.MatchSeverity.BLOCK)
            .toList();
    }
    
    public BlacklistMatch getHighestSeverityMatch() {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        
        return matches.stream()
            .max((m1, m2) -> Integer.compare(
                getSeverityOrder(m1.getSeverity()),
                getSeverityOrder(m2.getSeverity())
            ))
            .orElse(null);
    }
    
    private int getSeverityOrder(BlacklistMatch.MatchSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 4;
            case BLOCK -> 3;
            case WARNING -> 2;
            case INFO -> 1;
        };
    }
    
    public Double getHighestMatchConfidence() {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        
        return matches.stream()
            .filter(match -> match.getMatchConfidence() != null)
            .mapToDouble(BlacklistMatch::getMatchConfidence)
            .max()
            .orElse(0.0);
    }
    
    public boolean requiresManualReview() {
        return hasHighSeverityMatches() || 
               hasRegulatoryMatches() ||
               (hasMatches() && getHighestMatchConfidence() != null && 
                getHighestMatchConfidence() < 0.95); // Low confidence matches
    }
    
    public String getPrimaryBlockReason() {
        BlacklistMatch highestMatch = getHighestSeverityMatch();
        return highestMatch != null ? highestMatch.getReason() : null;
    }
}