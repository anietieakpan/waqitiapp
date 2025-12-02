package com.waqiti.tax.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tax_forms", indexes = {
    @Index(name = "idx_tax_forms_return_id", columnList = "tax_return_id"),
    @Index(name = "idx_tax_forms_form_type", columnList = "form_type"),
    @Index(name = "idx_tax_forms_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxForm {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_return_id", nullable = false)
    private TaxReturn taxReturn;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "form_type", nullable = false)
    private FormType formType;
    
    @Column(name = "form_number", nullable = false)
    private String formNumber; // e.g., "1040", "Schedule D", "8949"
    
    @Column(name = "form_name", nullable = false)
    private String formName;
    
    @Column(name = "form_data", columnDefinition = "TEXT", nullable = false)
    private String formData; // JSON representation of form fields
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FormStatus status = FormStatus.DRAFT;
    
    @Column(name = "version")
    private String version;
    
    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;
    
    @Column(name = "sequence_number")
    private Integer sequenceNumber;
    
    @Column(name = "is_final", nullable = false)
    @Builder.Default
    private Boolean isFinal = false;
    
    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors; // JSON array of validation errors
    
    @Column(name = "checksum")
    private String checksum;
    
    @Column(name = "pdf_path")
    private String pdfPath; // Path to generated PDF
    
    @Column(name = "xml_data", columnDefinition = "TEXT")
    private String xmlData; // IRS XML format for e-filing
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    
    public enum FormType {
        FORM_1040("Form 1040", "Individual Income Tax Return"),
        FORM_1040_SR("Form 1040-SR", "Senior Tax Return"),
        SCHEDULE_A("Schedule A", "Itemized Deductions"),
        SCHEDULE_B("Schedule B", "Interest and Dividend Income"),
        SCHEDULE_C("Schedule C", "Business Income or Loss"),
        SCHEDULE_D("Schedule D", "Capital Gains and Losses"),
        SCHEDULE_E("Schedule E", "Rental and Royalty Income"),
        FORM_8949("Form 8949", "Sales and Other Dispositions of Capital Assets"),
        FORM_8938("Form 8938", "Foreign Financial Assets"),
        FORM_3800("Form 3800", "General Business Credit"),
        FORM_1116("Form 1116", "Foreign Tax Credit"),
        SCHEDULE_SE("Schedule SE", "Self-Employment Tax"),
        FORM_4868("Form 4868", "Extension of Time to File"),
        FORM_1040X("Form 1040X", "Amended Return"),
        STATE_RETURN("State Return", "State Income Tax Return");
        
        private final String code;
        private final String description;
        
        FormType(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum FormStatus {
        DRAFT,
        IN_PROGRESS,
        COMPLETED,
        VALIDATED,
        SUBMITTED,
        ACCEPTED,
        REJECTED,
        AMENDED
    }
}