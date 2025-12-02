package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Credential Stuffing Detection Result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialStuffingResult {

    private boolean detected;
    private Double confidence;
    private List<String> affectedAccounts;
    private Integer attemptRate;
    private String attackPattern;
    private String sourceIpAddress;
    private Integer totalAttempts;
    private Integer uniqueAccounts;
}
