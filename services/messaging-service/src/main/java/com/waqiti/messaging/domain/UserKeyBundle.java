package com.waqiti.messaging.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_key_bundles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserKeyBundle {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;
    
    @Column(name = "identity_key", nullable = false)
    private String identityKey;
    
    @Column(name = "signed_pre_key_id", nullable = false)
    private Integer signedPreKeyId;
    
    @Column(name = "signed_pre_key", nullable = false)
    private String signedPreKey;
    
    @Column(name = "signed_pre_key_signature", nullable = false)
    private String signedPreKeySignature;
    
    @Column(name = "signed_pre_key_created_at", nullable = false)
    private LocalDateTime signedPreKeyCreatedAt;
    
    @OneToMany(mappedBy = "keyBundle", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PreKey> preKeys = new ArrayList<>();
    
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    
    @Column(name = "registration_id", nullable = false)
    private Integer registrationId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastActiveAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void addPreKey(PreKey preKey) {
        preKeys.add(preKey);
        preKey.setKeyBundle(this);
    }
    
    public void removePreKey(PreKey preKey) {
        preKeys.remove(preKey);
        preKey.setKeyBundle(null);
    }
    
    public PreKey getOneTimePreKey() {
        return preKeys.stream()
            .filter(pk -> !pk.getUsed())
            .findFirst()
            .orElse(null);
    }
    
    public void updateActivity() {
        this.lastActiveAt = LocalDateTime.now();
    }
    
    public boolean needsPreKeyRefill() {
        long availablePreKeys = preKeys.stream()
            .filter(pk -> !pk.getUsed())
            .count();
        return availablePreKeys < 10; // Threshold for refill
    }
}