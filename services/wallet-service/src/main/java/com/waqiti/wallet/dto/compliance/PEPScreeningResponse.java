package com.waqiti.wallet.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PEPScreeningResponse {
    private String screeningId;
    private boolean isPEP;
    private String pepType;
    private Map<String, Object> pepDetails;
}