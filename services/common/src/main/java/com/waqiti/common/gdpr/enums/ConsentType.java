package com.waqiti.common.gdpr.enums;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Consent Types
 *
 * Defines the types of consent that can be granted or revoked by data subjects
 * per GDPR Article 7 (Conditions for consent)
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-19
 */
public enum ConsentType {
    /**
     * Essential processing required for service delivery
     * Cannot be revoked while using the service
     */
    ESSENTIAL("Essential Service Operations", true),

    /**
     * Marketing communications and promotional materials
     * Article 7(3) - Can be withdrawn at any time
     */
    MARKETING("Marketing Communications", false),

    /**
     * Analytics and service improvement
     * Article 6(1)(a) - Requires explicit consent
     */
    ANALYTICS("Analytics and Performance", false),

    /**
     * Personalization and recommendations
     */
    PERSONALIZATION("Personalized Experience", false),

    /**
     * Third-party data sharing
     * Article 6(1)(a) - Explicit consent required
     */
    THIRD_PARTY_SHARING("Third Party Data Sharing", false),

    /**
     * Profiling and automated decision-making
     * Article 22 - Special consent required
     */
    PROFILING("Profiling and Automated Decisions", false),

    /**
     * Location data processing
     * Article 9 - Special category data
     */
    LOCATION_DATA("Location Data Processing", false),

    /**
     * Biometric data processing
     * Article 9 - Special category data
     */
    BIOMETRIC_DATA("Biometric Data Processing", false);

    private final String description;
    private final boolean required;

    ConsentType(String description, boolean required) {
        this.description = description;
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isOptional() {
        return !required;
    }
}
