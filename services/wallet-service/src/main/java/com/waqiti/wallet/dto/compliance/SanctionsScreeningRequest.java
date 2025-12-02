package com.waqiti.wallet.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanctionsScreeningRequest {
    private String userId;
    private String fullName;
    private LocalDateTime dateOfBirth;
    private String nationality;
    private List<String> addresses;
    private List<String> screeningLists;
    private LocalDateTime timestamp;
}