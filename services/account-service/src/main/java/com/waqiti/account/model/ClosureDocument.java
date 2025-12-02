package com.waqiti.account.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Closure Document Entity
 *
 * Documents associated with account closure (statements, confirmations, etc.)
 */
@Entity
@Table(name = "closure_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosureDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "closure_id", nullable = false)
    private UUID closureId;

    @Column(name = "document_type", length = 50)
    private String documentType;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "document_path", length = 500)
    private String documentPath;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
