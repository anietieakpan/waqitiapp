package com.waqiti.common.security.awareness.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDateTime;
import java.util.*;

/**
 * PCI DSS REQ 12.6.1 - Security Training Module Entity
 *
 * Represents a security awareness training module that can be assigned to employees.
 */
@Entity
@Table(
        name = "security_training_modules",
        indexes = {
                @Index(name = "idx_training_module_code", columnList = "module_code"),
                @Index(name = "idx_training_module_active", columnList = "is_active")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class SecurityTrainingModule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "module_code", nullable = false, unique = true, length = 50)
    private String moduleCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "pci_requirement", length = 20)
    private String pciRequirement;

    @Column(name = "is_mandatory", nullable = false)
    @Builder.Default
    private Boolean isMandatory = true;

    @Type(JsonBinaryType.class)
    @Column(name = "target_roles", columnDefinition = "jsonb")
    private List<String> targetRoles;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "passing_score_percentage", nullable = false)
    @Builder.Default
    private Integer passingScorePercentage = 80;

    @Column(name = "content_url")
    private String contentUrl;

    @Type(JsonBinaryType.class)
    @Column(name = "content_sections", columnDefinition = "jsonb")
    private List<Map<String, Object>> contentSections;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;
}