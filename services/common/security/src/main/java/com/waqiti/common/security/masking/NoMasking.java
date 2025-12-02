package com.waqiti.common.security.masking;

import java.lang.annotation.*;

/**
 * Annotation to exclude specific methods from automatic data masking
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoMasking {
}