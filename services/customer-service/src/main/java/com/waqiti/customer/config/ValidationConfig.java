package com.waqiti.customer.config;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.HibernateValidator;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import jakarta.validation.Validator;

/**
 * Validation Configuration for Customer Service.
 * Configures Bean Validation with custom message sources,
 * Hibernate Validator, and method-level validation.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@Slf4j
public class ValidationConfig {

    /**
     * Configures the validator factory bean with custom message source.
     * Uses Hibernate Validator as the implementation.
     *
     * @return LocalValidatorFactoryBean
     */
    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.setProviderClass(HibernateValidator.class);
        validatorFactoryBean.setValidationMessageSource(validationMessageSource());

        // Enable fail fast mode for development (optional)
        // validatorFactoryBean.getValidationPropertyMap().put("hibernate.validator.fail_fast", "true");

        log.info("LocalValidatorFactoryBean configured with Hibernate Validator");
        return validatorFactoryBean;
    }

    /**
     * Configures message source for validation error messages.
     * Supports internationalization (i18n) for validation messages.
     *
     * @return MessageSource for validation messages
     */
    @Bean
    public MessageSource validationMessageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages/validation");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600);
        messageSource.setFallbackToSystemLocale(true);

        log.info("Validation message source configured with basename: messages/validation");
        return messageSource;
    }

    /**
     * Enables method-level validation using @Validated annotation.
     * Allows validation of method parameters and return values.
     *
     * @return MethodValidationPostProcessor
     */
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setValidator(validator());
        log.info("Method validation post processor configured");
        return processor;
    }

    /**
     * Provides a Validator bean for programmatic validation.
     *
     * @return Validator instance
     */
    @Bean
    public Validator validatorInstance() {
        return validator().getValidator();
    }
}
