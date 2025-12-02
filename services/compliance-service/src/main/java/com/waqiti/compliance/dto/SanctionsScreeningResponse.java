package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionsScreeningResponse {
    private UUID screeningId;
    private String entityId;
    private String entityType;
    private Boolean hasMatches;
    private Integer matchCount;
    private String status; // CLEAR, POTENTIAL_MATCH, CONFIRMED_MATCH
    private List<SanctionsMatch> matches;
    private LocalDateTime screenedAt;
    private String screenedBy;
    private Double highestMatchScore;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SanctionsMatch {
    private String listName; // OFAC, EU, UN, etc.
    private String matchedName;
    private Double matchScore;
    private String matchType;
    private String sanctionProgram;
    private String remarks;
}