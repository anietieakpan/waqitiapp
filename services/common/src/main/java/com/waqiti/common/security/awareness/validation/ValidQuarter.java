package com.waqiti.common.security.awareness.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = QuarterValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidQuarter {
    String message() default "Quarter must be between 1 and 4";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}