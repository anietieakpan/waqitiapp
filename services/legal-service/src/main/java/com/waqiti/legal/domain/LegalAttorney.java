package com.waqiti.legal.domain;

import com.waqiti.legal.service.LegalOrderAssignmentService.SkillLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Legal Attorney Entity
 *
 * Represents an attorney in the legal department with their
 * skills, specialties, and workload information for intelligent assignment.
 *
 * @author Waqiti Legal Technology Team
 * @version 1.0.0
 * @since 2025-10-23
 */
@Entity
@Table(name = "legal_attorneys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalAttorney {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attorney_id", unique = true, nullable = false)
    private String attorneyId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", nullable = false)
    private SkillLevel skillLevel;

    @ElementCollection
    @CollectionTable(name = "attorney_specialties", joinColumns = @JoinColumn(name = "attorney_id"))
    @Column(name = "specialty")
    private Set<String> specialties;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "active_workload_count")
    @Builder.Default
    private Integer activeWorkloadCount = 0;

    @Column(name = "max_workload_capacity")
    @Builder.Default
    private Integer maxWorkloadCapacity = 15;

    @Column(name = "success_rate")
    private Double successRate;

    @Column(name = "average_completion_days")
    private Double averageCompletionDays;

    @Column(name = "total_cases_handled")
    @Builder.Default
    private Integer totalCasesHandled = 0;

    @Column(name = "bar_number")
    private String barNumber;

    @Column(name = "jurisdiction")
    private String jurisdiction;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Increment active workload when case assigned
     */
    public void incrementActiveWorkload() {
        if (activeWorkloadCount == null) {
            activeWorkloadCount = 0;
        }
        activeWorkloadCount++;
    }

    /**
     * Decrement active workload when case completed/reassigned
     */
    public void decrementActiveWorkload() {
        if (activeWorkloadCount != null && activeWorkloadCount > 0) {
            activeWorkloadCount--;
        }
    }

    /**
     * Check if attorney is at capacity
     */
    public boolean isAtCapacity() {
        return activeWorkloadCount != null &&
               maxWorkloadCapacity != null &&
               activeWorkloadCount >= maxWorkloadCapacity;
    }

    /**
     * Calculate availability percentage (0-100)
     */
    public double getAvailabilityPercentage() {
        if (activeWorkloadCount == null || maxWorkloadCapacity == null || maxWorkloadCapacity == 0) {
            return 100.0;
        }
        return 100.0 * (1.0 - ((double) activeWorkloadCount / maxWorkloadCapacity));
    }
}
