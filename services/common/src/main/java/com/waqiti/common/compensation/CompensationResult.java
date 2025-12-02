package com.waqiti.common.compensation;

import lombok.Builder;
import lombok.Data;

/**
 * Result object for compensation execution.
 * Provides detailed information about compensation success/failure.
 */
@Data
@Builder
public class CompensationResult {

    private boolean success;
    private CompensationTransaction compensation;
    private String message;
    private Exception error;
    private CompensationResultType resultType;

    public enum CompensationResultType {
        SUCCESS,
        FAILED,
        RETRYING,
        REQUIRES_MANUAL_INTERVENTION
    }

    public static CompensationResult success(CompensationTransaction compensation) {
        return CompensationResult.builder()
            .success(true)
            .compensation(compensation)
            .resultType(CompensationResultType.SUCCESS)
            .message("Compensation completed successfully")
            .build();
    }

    public static CompensationResult failed(CompensationTransaction compensation, Exception error) {
        return CompensationResult.builder()
            .success(false)
            .compensation(compensation)
            .error(error)
            .resultType(CompensationResultType.FAILED)
            .message("Compensation failed: " + error.getMessage())
            .build();
    }

    public static CompensationResult retrying(CompensationTransaction compensation) {
        return CompensationResult.builder()
            .success(false)
            .compensation(compensation)
            .resultType(CompensationResultType.RETRYING)
            .message("Compensation will be retried")
            .build();
    }

    public static CompensationResult requiresManualIntervention(
            CompensationTransaction compensation, Exception error) {
        return CompensationResult.builder()
            .success(false)
            .compensation(compensation)
            .error(error)
            .resultType(CompensationResultType.REQUIRES_MANUAL_INTERVENTION)
            .message("Compensation requires manual intervention: " + error.getMessage())
            .build();
    }
}
