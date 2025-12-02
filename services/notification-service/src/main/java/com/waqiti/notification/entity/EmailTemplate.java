package com.waqiti.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Email Template Entity
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
@Entity
@Table(name = "email_templates", indexes = {
    @Index(name = "idx_email_template_name", columnList = "name"),
    @Index(name = "idx_email_template_category", columnList = "category"),
    @Index(name = "idx_email_template_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "html_content", columnDefinition = "TEXT")
    private String htmlContent;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "locale", length = 10)
    private String locale;

    @ElementCollection
    @CollectionTable(name = "email_template_variables", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "variable_name")
    private List<String> variables;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
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
}
