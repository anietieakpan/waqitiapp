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
 * GDPR Risk Level Classification
 *
 * Used for Data Protection Impact Assessments (DPIA) per Article 35
 * and breach notification decisions per Articles 33/34
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-19
 */
public enum RiskLevel {
    /**
     * Low risk - No significant impact on rights and freedoms
     * No breach notification required
     */
    LOW(1, "Low Risk", false, false),

    /**
     * Medium risk - Some potential impact
     * Internal notification recommended
     */
    MEDIUM(2, "Medium Risk", false, true),

    /**
     * High risk - Significant impact possible
     * Article 35 - DPIA may be required
     * Article 33 - Supervisory authority notification within 72 hours
     */
    HIGH(3, "High Risk", true, true),

    /**
     * Critical risk - Severe impact likely
     * Article 35 - DPIA required
     * Article 33/34 - Immediate notification to authority and data subjects
     */
    CRITICAL(4, "Critical Risk", true, true);

    private final int severity;
    private final String description;
    private final boolean requiresDPIA;
    private final boolean requiresBreachNotification;

    RiskLevel(int severity, String description, boolean requiresDPIA, boolean requiresBreachNotification) {
        this.severity = severity;
        this.description = description;
        this.requiresDPIA = requiresDPIA;
        this.requiresBreachNotification = requiresBreachNotification;
    }

    public int getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public boolean requiresDPIA() {
        return requiresDPIA;
    }

    public boolean requiresBreachNotification() {
        return requiresBreachNotification;
    }

    public boolean isHigherThan(RiskLevel other) {
        return this.severity > other.severity;
    }
}
