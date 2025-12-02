package com.waqiti.customer.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Customer Document Entity
 *
 * Represents documents uploaded or associated with a customer including
 * identity documents, financial statements, contracts, and other files.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_document", indexes = {
    @Index(name = "idx_customer_document_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_document_type", columnList = "document_type"),
    @Index(name = "idx_customer_document_uploaded", columnList = "uploaded_at")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "documentId")
public class CustomerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_id", unique = true, nullable = false, length = 100)
    private String documentId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "is_sensitive")
    @Builder.Default
    private Boolean isSensitive = false;

    @Column(name = "encryption_algorithm", length = 50)
    private String encryptionAlgorithm;

    @Column(name = "retention_period_days")
    private Integer retentionPeriodDays;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Enums
    public enum DocumentType {
        IDENTITY_PROOF,
        ADDRESS_PROOF,
        INCOME_PROOF,
        BANK_STATEMENT,
        TAX_RETURN,
        EMPLOYMENT_LETTER,
        BUSINESS_REGISTRATION,
        ARTICLES_OF_INCORPORATION,
        FINANCIAL_STATEMENT,
        TRADE_LICENSE,
        CONTRACT,
        AGREEMENT,
        POWER_OF_ATTORNEY,
        COURT_ORDER,
        COMPLIANCE_DOCUMENT,
        KYC_DOCUMENT,
        AML_DOCUMENT,
        SIGNATURE_CARD,
        PHOTO,
        OTHER
    }

    /**
     * Check if document is sensitive
     *
     * @return true if sensitive
     */
    public boolean isSensitive() {
        return isSensitive != null && isSensitive;
    }

    /**
     * Check if document is encrypted
     *
     * @return true if encrypted
     */
    public boolean isEncrypted() {
        return encryptionAlgorithm != null && !encryptionAlgorithm.isEmpty();
    }

    /**
     * Check if document has expiry date
     *
     * @return true if expiry date is set
     */
    public boolean hasExpiryDate() {
        return expiryDate != null;
    }

    /**
     * Check if document is expired
     *
     * @return true if expired
     */
    public boolean isExpired() {
        if (expiryDate == null) {
            return false;
        }
        return LocalDate.now().isAfter(expiryDate);
    }

    /**
     * Check if document should be retained
     *
     * @return true if within retention period
     */
    public boolean shouldRetain() {
        if (retentionPeriodDays == null) {
            return true; // No retention policy means keep indefinitely
        }
        LocalDateTime retentionDeadline = createdAt.plusDays(retentionPeriodDays);
        return LocalDateTime.now().isBefore(retentionDeadline);
    }

    /**
     * Get days until retention expiry
     *
     * @return days until retention expiry, null if no retention period
     */
    public Long getDaysUntilRetentionExpiry() {
        if (retentionPeriodDays == null || createdAt == null) {
            return null;
        }
        LocalDateTime retentionDeadline = createdAt.plusDays(retentionPeriodDays);
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), retentionDeadline);
        return days > 0 ? days : 0;
    }

    /**
     * Get file size in KB
     *
     * @return file size in KB
     */
    public Double getFileSizeKB() {
        if (fileSizeBytes == null) {
            return null;
        }
        return fileSizeBytes / 1024.0;
    }

    /**
     * Get file size in MB
     *
     * @return file size in MB
     */
    public Double getFileSizeMB() {
        if (fileSizeBytes == null) {
            return null;
        }
        return fileSizeBytes / (1024.0 * 1024.0);
    }

    /**
     * Add tag to document
     *
     * @param tag the tag to add
     */
    public void addTag(String tag) {
        if (this.tags == null) {
            this.tags = new String[]{tag};
        } else {
            String[] newTags = new String[this.tags.length + 1];
            System.arraycopy(this.tags, 0, newTags, 0, this.tags.length);
            newTags[this.tags.length] = tag;
            this.tags = newTags;
        }
    }

    /**
     * Set metadata value
     *
     * @param key the metadata key
     * @param value the metadata value
     */
    public void setMetadataValue(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Get metadata value
     *
     * @param key the metadata key
     * @return the metadata value, or null if not found
     */
    public Object getMetadataValue(String key) {
        if (this.metadata == null) {
            return null;
        }
        return this.metadata.get(key);
    }
}
