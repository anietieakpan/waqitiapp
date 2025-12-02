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
public class PEPScreeningResult {
    private String screeningId;
    private String userId;
    private boolean isPEP;
    private String pepType;
    private Map<String, Object> pepDetails;
    private LocalDateTime timestamp;
}