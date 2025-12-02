package com.waqiti.tax.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tax_documents", indexes = {
    @Index(name = "idx_tax_documents_return_id", columnList = "tax_return_id"),
    @Index(name = "idx_tax_documents_type", columnList = "document_type"),
    @Index(name = "idx_tax_documents_tax_year", columnList = "tax_year")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxDocument {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_return_id", nullable = false)
    private TaxReturn taxReturn;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;
    
    @Column(name = "document_name", nullable = false)
    private String documentName;
    
    @Column(name = "document_data", columnDefinition = "TEXT")
    private String documentData; // Encrypted JSON data
    
    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;
    
    @Column(name = "issuer_name")
    private String issuerName;
    
    @Column(name = "issuer_tin")
    private String issuerTin;
    
    @Column(name = "recipient_tin")
    private String recipientTin;
    
    @Column(name = "form_id")
    private String formId; // External form ID from provider
    
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;
    
    @Column(name = "verification_status")
    private String verificationStatus;
    
    @Column(name = "source")
    private String source; // IRS, employer, manual, etc.
    
    @Column(name = "file_path")
    private String filePath; // Path to stored file
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "checksum")
    private String checksum;
    
    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    public enum DocumentType {
        W2("W-2 Wage and Tax Statement"),
        FORM_1099("1099 Forms"),
        FORM_1099K("1099-K Payment Card Transactions"),
        FORM_1099B("1099-B Investment Sales"),
        FORM_1099INT("1099-INT Interest Income"),
        FORM_1099DIV("1099-DIV Dividend Income"),
        FORM_8949("Form 8949 Crypto Gains/Losses"),
        SCHEDULE_C("Schedule C Business Income"),
        SCHEDULE_D("Schedule D Capital Gains"),
        SCHEDULE_E("Schedule E Rental Income"),
        FORM_1095A("1095-A Health Insurance"),
        FORM_1098("1098 Mortgage Interest"),
        FORM_1098T("1098-T Tuition Statement"),
        RECEIPT("Tax Receipt"),
        OTHER("Other Tax Document");
        
        private final String description;
        
        DocumentType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}