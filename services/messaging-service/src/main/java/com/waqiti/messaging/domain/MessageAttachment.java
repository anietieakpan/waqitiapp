package com.waqiti.messaging.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;
    
    @Column(name = "attachment_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AttachmentType type;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    
    @Column(name = "encrypted_url", nullable = false)
    private String encryptedUrl;
    
    @Column(name = "encrypted_key", nullable = false)
    private String encryptedKey;
    
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;
    
    @Column(name = "thumbnail_key")
    private String thumbnailKey;
    
    @Column(name = "checksum")
    private String checksum;
    
    @Column(name = "width")
    private Integer width;
    
    @Column(name = "height")
    private Integer height;
    
    @Column(name = "duration")
    private Integer duration; // For audio/video in seconds
    
    @Column(name = "latitude")
    private Double latitude; // For location
    
    @Column(name = "longitude")
    private Double longitude; // For location
    
    @Column(name = "address")
    private String address; // For location
    
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "is_encrypted", nullable = false)
    private Boolean isEncrypted = true;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}

enum AttachmentType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    LOCATION,
    CONTACT,
    PAYMENT_RECEIPT
}