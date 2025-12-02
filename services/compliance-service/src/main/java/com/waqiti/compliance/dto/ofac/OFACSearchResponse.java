package com.waqiti.compliance.dto.ofac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response from OFAC sanctions search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OFACSearchResponse {
    
    private String searchId;
    private boolean hasMatches;
    private int matchCount;
    private Double highestScore;
    private List<SanctionMatch> matches;
    private LocalDateTime searchTimestamp;
    private boolean requiresManualReview;
    private String errorMessage;
    
    /**
     * Individual sanctions match
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionMatch {
        private String entityId;
        private String sdnId;
        private String name;
        private String type; // Individual, Entity, Vessel, Aircraft
        private Double matchScore;
        private String program; // Sanctions program (e.g., IRAN, DPRK, RUSSIA)
        private List<String> aliases;
        private List<String> addresses;
        private String remarks;
        private LocalDateTime listingDate;
        private MatchDetails matchDetails;
    }
    
    /**
     * Details about how the match was determined
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchDetails {
        private boolean nameMatch;
        private boolean addressMatch;
        private boolean identifierMatch;
        private Double nameScore;
        private Double addressScore;
        private String matchType; // EXACT, FUZZY, PHONETIC
        private List<String> matchedFields;
    }
}