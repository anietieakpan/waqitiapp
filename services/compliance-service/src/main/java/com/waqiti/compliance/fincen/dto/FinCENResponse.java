package com.waqiti.compliance.fincen.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Internal response from FinCEN API
 */
@Data
@Builder
public class FinCENResponse {
    private boolean success;
    private String bsaId;
    private String errorCode;
    private String errorMessage;
}
