package com.waqiti.compliance.fincen.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Result of CTR filing with FinCEN
 */
@Data
@Builder
public class CTRFilingResult {
    private boolean success;
    private String bsaId;
    private LocalDateTime filedDate;
    private String errorCode;
    private String errorMessage;
}
