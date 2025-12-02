package com.waqiti.compliance.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PEPScreeningResult {

    private boolean pepMatch;
    private String pepDetails;
    private String pepCategory;
    private Double matchScore;
    private String position;
    private String country;
    private LocalDateTime screenedAt;
    private String errorMessage;
    private Map<String, Object> additionalInfo;

    public boolean isPEPMatch() {
        return pepMatch;
    }

    public String getPepDetails() {
        return pepDetails;
    }

    public static PEPScreeningResult error(String errorMessage) {
        return PEPScreeningResult.builder()
                .pepMatch(false)
                .errorMessage(errorMessage)
                .pepDetails("Error during PEP screening: " + errorMessage)
                .screenedAt(LocalDateTime.now())
                .build();
    }

    public static PEPScreeningResult notPEP() {
        return PEPScreeningResult.builder()
                .pepMatch(false)
                .pepDetails("No PEP matches found")
                .matchScore(0.0)
                .screenedAt(LocalDateTime.now())
                .build();
    }
}
