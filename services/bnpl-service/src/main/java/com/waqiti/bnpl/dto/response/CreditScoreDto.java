package com.waqiti.bnpl.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for credit score
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreDto {

    private String userId;
    private Integer score;
    private String provider;
    private LocalDateTime retrievedAt;
    private boolean incomeVerified;
    private String employmentStatus;
    private Integer accountAgeMonths;
    private Integer previousDefaults;
}