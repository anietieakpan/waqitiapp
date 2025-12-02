package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Customer Profile Entity
 *
 * Stores customer behavioral data, transaction patterns, and risk metrics
 * for fraud detection and risk assessment.
 *
 * PRODUCTION-GRADE ENTITY
 * - Optimistic locking with @Version
 * - Audit fields with JPA Auditing
 * - Indexed fields for query performance
 * - Immutable creation timestamp
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Entity
@Table(name = "customer_profiles", indexes = {
    @Index(name = "idx_customer_id", columnList = "customer_id"),
    @Index(name = "idx_risk_level", columnList = "current_risk_level"),
    @Index(name = "idx_last_transaction_date", columnList = "last_transaction_date"),
    @Index(name = "idx_created_date", columnList = "created_date")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optimistic locking version for concurrent update protection
     */
    @Version
    private Long version;

    /**
     * Customer ID (reference to user service)
     */
    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    /**
     * Transaction Statistics
     */
    @Column(name = "total_transactions", nullable = false)
    @Builder.Default
    private Long totalTransactions = 0L;

    @Column(name = "total_transaction_volume", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalTransactionVolume = BigDecimal.ZERO;

    @Column(name = "average_transaction_amount", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal averageTransactionAmount = BigDecimal.ZERO;

    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;

    @Column(name = "last_transaction_amount", precision = 19, scale = 4)
    private BigDecimal lastTransactionAmount;

    /**
     * Risk Metrics
     */
    @Column(name = "average_risk_score", nullable = false)
    @Builder.Default
    private Double averageRiskScore = 0.0;

    @Column(name = "current_risk_level", nullable = false, length = 20)
    @Builder.Default
    private String currentRiskLevel = "LOW";

    @Column(name = "fraud_count", nullable = false)
    @Builder.Default
    private Integer fraudCount = 0;

    @Column(name = "last_fraud_date")
    private LocalDateTime lastFraudDate;

    /**
     * Behavioral Patterns (stored as JSON or separate table in production)
     */
    @Column(name = "typical_transaction_hours", columnDefinition = "TEXT")
    private String typicalTransactionHours; // Comma-separated hours

    @Column(name = "typical_transaction_days", columnDefinition = "TEXT")
    private String typicalTransactionDays; // Comma-separated days

    /**
     * Location History
     */
    @Column(name = "known_countries", columnDefinition = "TEXT")
    private String knownCountries; // Comma-separated country codes

    @Column(name = "last_known_country", length = 3)
    private String lastKnownCountry;

    /**
     * Device History
     */
    @Column(name = "known_devices", columnDefinition = "TEXT")
    private String knownDevices; // Comma-separated device fingerprints

    @Column(name = "last_known_device", length = 255)
    private String lastKnownDevice;

    /**
     * Account Information
     */
    @Column(name = "account_age", nullable = false)
    @Builder.Default
    private Integer accountAge = 0;

    @Column(name = "kyc_status", length = 20)
    private String kycStatus;

    @Column(name = "kyc_completion_date")
    private LocalDateTime kycCompletionDate;

    /**
     * Audit Fields
     */
    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    /**
     * Helper method to add typical transaction hour
     */
    public void addTypicalTransactionHour(int hour) {
        Set<Integer> hours = getTypicalHoursSet();
        hours.add(hour);
        // Keep only last 50 hours
        if (hours.size() > 50) {
            Iterator<Integer> it = hours.iterator();
            it.next();
            it.remove();
        }
        this.typicalTransactionHours = hours.stream()
            .map(String::valueOf)
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }

    /**
     * Helper method to add typical transaction day
     */
    public void addTypicalTransactionDay(int dayOfWeek) {
        Set<Integer> days = getTypicalDaysSet();
        days.add(dayOfWeek);
        this.typicalTransactionDays = days.stream()
            .map(String::valueOf)
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }

    /**
     * Helper method to add country to history
     */
    public void addCountryToHistory(String countryCode) {
        Set<String> countries = getKnownCountriesSet();
        countries.add(countryCode);
        // Keep only last 20 countries
        if (countries.size() > 20) {
            Iterator<String> it = countries.iterator();
            it.next();
            it.remove();
        }
        this.knownCountries = String.join(",", countries);
    }

    /**
     * Helper method to add device to history
     */
    public void addDeviceToHistory(String deviceFingerprint) {
        Set<String> devices = getKnownDevicesSet();
        devices.add(deviceFingerprint);
        // Keep only last 10 devices
        if (devices.size() > 10) {
            Iterator<String> it = devices.iterator();
            it.next();
            it.remove();
        }
        this.knownDevices = String.join(",", devices);
    }

    /**
     * Update velocity pattern
     */
    public void updateVelocityPattern(LocalDateTime transactionTime) {
        // Calculate time since last transaction
        if (this.lastTransactionDate != null) {
            long minutesSinceLast = java.time.temporal.ChronoUnit.MINUTES
                .between(this.lastTransactionDate, transactionTime);
            // Store velocity metrics (in production, use separate table)
        }
    }

    /**
     * Helper methods to parse stored strings
     */
    private Set<Integer> getTypicalHoursSet() {
        if (typicalTransactionHours == null || typicalTransactionHours.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Integer> hours = new LinkedHashSet<>();
        for (String h : typicalTransactionHours.split(",")) {
            try {
                hours.add(Integer.parseInt(h.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid entries
            }
        }
        return hours;
    }

    private Set<Integer> getTypicalDaysSet() {
        if (typicalTransactionDays == null || typicalTransactionDays.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Integer> days = new LinkedHashSet<>();
        for (String d : typicalTransactionDays.split(",")) {
            try {
                days.add(Integer.parseInt(d.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid entries
            }
        }
        return days;
    }

    private Set<String> getKnownCountriesSet() {
        if (knownCountries == null || knownCountries.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Arrays.asList(knownCountries.split(",")));
    }

    private Set<String> getKnownDevicesSet() {
        if (knownDevices == null || knownDevices.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Arrays.asList(knownDevices.split(",")));
    }

    @PrePersist
    protected void onCreate() {
        if (createdDate == null) {
            createdDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }
}
