package com.waqiti.compliance.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OFACScreeningResult {

    private boolean clean;
    private String matchDetails;
    private Double matchScore;
    private String sanctionsList;
    private LocalDateTime screenedAt;
    private String errorMessage;

    public boolean isClean() {
        return clean;
    }

    public String getMatchDetails() {
        return matchDetails;
    }

    public static OFACScreeningResult error(String errorMessage) {
        return OFACScreeningResult.builder()
                .clean(false)
                .errorMessage(errorMessage)
                .matchDetails("Error during screening: " + errorMessage)
                .screenedAt(LocalDateTime.now())
                .build();
    }

    public static OFACScreeningResult clean() {
        return OFACScreeningResult.builder()
                .clean(true)
                .matchDetails("No OFAC matches found")
                .matchScore(0.0)
                .screenedAt(LocalDateTime.now())
                .build();
    }
}
