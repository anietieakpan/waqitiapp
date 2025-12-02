package com.waqiti.common.fraud.profiling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map; /**
 * Profile update record
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdate {
    private LocalDateTime updateDate;
    private double previousScore;
    private double newScore;
    private String updateReason;
    private Map<String, Object> changedFields;
}
