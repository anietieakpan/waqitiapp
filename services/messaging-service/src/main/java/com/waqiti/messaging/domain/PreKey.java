package com.waqiti.messaging.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pre_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreKey {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @Column(name = "key_id", nullable = false)
    private Integer keyId;
    
    @Column(name = "public_key", nullable = false)
    private String publicKey;
    
    @Column(name = "private_key", nullable = false)
    private String privateKey;
    
    @Column(name = "used", nullable = false)
    private Boolean used = false;
    
    @Column(name = "used_at")
    private LocalDateTime usedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "key_bundle_id", nullable = false)
    private UserKeyBundle keyBundle;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public void markAsUsed() {
        this.used = true;
        this.usedAt = LocalDateTime.now();
    }
}