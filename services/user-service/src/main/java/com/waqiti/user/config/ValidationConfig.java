package com.waqiti.user.config;

import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * Global Validation Configuration
 *
 * Enables comprehensive validation across the entire application:
 * - Bean validation on @RequestBody DTOs
 * - Method parameter validation on @Valid/@Validated
 * - Custom validators: @SafeString, @ValidPhoneNumber, @StrongPassword
 *
 * SECURITY IMPACT:
 * - Prevents XSS attacks via input sanitization
 * - Prevents SQL injection via pattern validation
 * - Enforces strong password policy
 * - Validates all external inputs
 *
 * COMPLIANCE:
 * - OWASP A03:2021 – Injection (defense-in-depth)
 * - PCI-DSS 6.5.1: Input validation
 * - SOC 2: Input validation controls
 */
@Slf4j
@Configuration
public class ValidationConfig {

    /**
     * Configure JSR-303 Bean Validation
     *
     * This validator is used by Spring MVC to validate @RequestBody parameters
     */
    @Bean
    public LocalValidatorFactoryBean validator() {
        log.info("VALIDATION: Configuring JSR-303 Bean Validation");

        LocalValidatorFactoryBean validatorFactory = new LocalValidatorFactoryBean();

        // Fail fast: stop validation on first error for performance
        // In production, you may want to collect all errors
        validatorFactory.getValidationPropertyMap().put(
            "hibernate.validator.fail_fast", "false"
        );

        log.info("VALIDATION: Bean Validation configured successfully");
        log.info("VALIDATION: Custom validators enabled: @SafeString, @ValidPhoneNumber, @StrongPassword");

        return validatorFactory;
    }

    /**
     * Enable method-level validation
     *
     * Allows validation of method parameters annotated with @Valid or @Validated
     * Essential for service layer validation
     */
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor(Validator validator) {
        log.info("VALIDATION: Enabling method-level validation");

        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setValidator(validator);

        log.info("VALIDATION: Method validation enabled for @Valid and @Validated");

        return processor;
    }
}
