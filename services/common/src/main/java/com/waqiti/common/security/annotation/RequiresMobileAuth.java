package com.waqiti.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Security annotation for mobile app authentication
 * Ensures only authenticated mobile clients can access sensitive endpoints
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('MOBILE_USER') and isAuthenticated()")
public @interface RequiresMobileAuth {
}