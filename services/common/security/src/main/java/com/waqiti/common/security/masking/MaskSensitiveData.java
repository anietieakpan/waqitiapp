package com.waqiti.common.security.masking;

import java.lang.annotation.*;

/**
 * Annotation to trigger data masking on method responses
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaskSensitiveData {
    /**
     * Whether to perform deep masking on nested objects
     */
    boolean deep() default true;
}