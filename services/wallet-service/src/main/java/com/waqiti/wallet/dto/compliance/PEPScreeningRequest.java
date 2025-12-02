package com.waqiti.wallet.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PEPScreeningRequest {
    private String userId;
    private String fullName;
    private LocalDateTime dateOfBirth;
    private String nationality;
    private LocalDateTime timestamp;
}