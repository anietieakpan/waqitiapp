package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErasureCertificate {
    private String id;
    private String userId;
    private LocalDateTime erasureDate;
    private String certificateHash;
}