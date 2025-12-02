package com.waqiti.compliance.model.sanctions;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sanctioned Entity Alias - Alternative Names and Variants
 *
 * Stores alternative names, aliases, and name variants for sanctioned entities.
 * Critical for fuzzy name matching to detect individuals using alternate identities.
 *
 * ALIAS TYPES:
 * - AKA (Also Known As) - confirmed alternative name
 * - FKA (Formerly Known As) - previous legal name
 * - NICKNAME - informal name or moniker
 * - WEAK_AKA - unconfirmed or possible alias
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sanctioned_entity_aliases", indexes = {
        @Index(name = "idx_aliases_entity", columnList = "sanctioned_entity_id"),
        @Index(name = "idx_aliases_normalized", columnList = "alias_name_normalized"),
        @Index(name = "idx_aliases_type", columnList = "alias_type"),
        @Index(name = "idx_aliases_quality", columnList = "alias_quality")
})
public class SanctionedEntityAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "sanctioned_entity_id", nullable = false)
    private UUID sanctionedEntityId;

    // Alias information
    @Column(name = "alias_type", nullable = false, length = 50)
    private String aliasType; // AKA, FKA, NICKNAME, WEAK_AKA, etc.

    @Column(name = "alias_name", nullable = false, length = 500)
    private String aliasName;

    @Column(name = "alias_name_normalized", nullable = false, length = 500)
    private String aliasNameNormalized;

    // Alias quality
    @Column(name = "alias_quality", length = 20)
    private String aliasQuality; // STRONG, WEAK, LOW

    // Metadata
    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    @Column(name = "language_code", length = 3)
    private String languageCode;

    @Column(name = "script_type", length = 20)
    private String scriptType; // LATIN, CYRILLIC, ARABIC, etc.

    // Audit
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isPrimary == null) {
            isPrimary = false;
        }
        if (aliasQuality == null) {
            aliasQuality = "STRONG";
        }
    }
}
