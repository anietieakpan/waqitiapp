package com.waqiti.common.security.awareness.dto;

import com.waqiti.common.security.awareness.domain.EmployeeTrainingRecord;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Training Completion Result DTO
 *
 * @author Waqiti Platform Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingCompletionResult {

    private UUID recordId;
    private UUID moduleId;
    private UUID employeeId;
    private EmployeeTrainingRecord.TrainingStatus status;
    private Integer score;
    private Boolean passed;
    private Integer durationMinutes;
    private LocalDateTime completedAt;
    private String certificateUrl;
    private Integer attemptsRemaining;
    private boolean maxAttemptsExceeded;

    /**
     * Create result for max attempts exceeded
     */
    public static TrainingCompletionResult maxAttemptsExceeded(EmployeeTrainingRecord record) {
        return TrainingCompletionResult.builder()
                .recordId(record.getId())
                .moduleId(record.getModuleId())
                .employeeId(record.getEmployeeId())
                .status(EmployeeTrainingRecord.TrainingStatus.FAILED)
                .passed(false)
                .maxAttemptsExceeded(true)
                .attemptsRemaining(0)
                .build();
    }
}