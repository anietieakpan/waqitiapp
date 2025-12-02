package com.waqiti.rewards.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validation annotation for program IDs
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Documented
@Constraint(validatedBy = ProgramIdValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidProgramId {

    String message() default "Invalid program ID format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Whether to check if program exists in database
     */
    boolean checkExists() default false;

    /**
     * Whether to check if program is active
     */
    boolean checkActive() default false;
}
