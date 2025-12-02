package com.waqiti.wallet.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionsScreeningResponse {
    private String screeningId;
    private boolean isMatch;
    private Double matchScore;
    private List<String> matchedLists;
    private Map<String, Object> matchDetails;
}