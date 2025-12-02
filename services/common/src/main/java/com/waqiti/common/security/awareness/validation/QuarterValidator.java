package com.waqiti.common.security.awareness.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class QuarterValidator implements ConstraintValidator<ValidQuarter, Integer> {

    @Override
    public boolean isValid(Integer quarter, ConstraintValidatorContext context) {
        if (quarter == null) {
            return false;
        }
        return quarter >= 1 && quarter <= 4;
    }
}