package com.waqiti.common.security.masking;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.*;

/**
 * Annotation to mark fields that should be masked in responses
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = MaskingSerializer.class)
public @interface MaskedData {
    MaskingSerializer.MaskingType value() default MaskingSerializer.MaskingType.GENERIC;
}