package com.waqiti.support.domain;

import com.waqiti.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"ticket", "message"})
public class TicketAttachment extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private TicketMessage message;
    
    @Column(nullable = false)
    private String fileName;
    
    @Column(nullable = false)
    private String fileType;
    
    @Column(nullable = false)
    private Long fileSize;
    
    @Column(nullable = false)
    private String s3Key;
    
    @Column(nullable = false)
    private String s3Bucket;
    
    private String contentType;
    
    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;
    
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    
    @Column(name = "virus_scan_status")
    @Enumerated(EnumType.STRING)
    private VirusScanStatus virusScanStatus = VirusScanStatus.PENDING;
    
    @Column(name = "virus_scan_result")
    private String virusScanResult;
    
    @Column(name = "is_deleted")
    private boolean isDeleted;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "download_count")
    private Integer downloadCount = 0;
    
    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;
    
    @PrePersist
    public void prePersist() {
        this.uploadedAt = LocalDateTime.now();
    }
    
    public void incrementDownloadCount() {
        this.downloadCount++;
        this.lastDownloadedAt = LocalDateTime.now();
    }
    
    public void markAsDeleted() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }
    
    public String getDownloadUrl() {
        // This would be generated using S3 presigned URL in service layer
        return null;
    }
}

