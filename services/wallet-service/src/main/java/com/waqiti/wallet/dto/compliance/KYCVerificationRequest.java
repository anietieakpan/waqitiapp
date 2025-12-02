package com.waqiti.wallet.dto.compliance;

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
public class KYCVerificationRequest {
    private String userId;
    private String firstName;
    private String lastName;
    private LocalDateTime dateOfBirth;
    private String nationality;
    private String documentType;
    private String documentNumber;
    private Map<String, Object> additionalData;
    private LocalDateTime timestamp;
}