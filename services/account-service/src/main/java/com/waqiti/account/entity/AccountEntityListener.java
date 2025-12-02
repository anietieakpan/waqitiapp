package com.waqiti.account.entity;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate; /**
 * Entity listener for Account-specific events
 */
@org.springframework.stereotype.Component
public class AccountEntityListener {
    
    @PrePersist
    public void prePersist(Account account) {
        // Custom pre-persist logic
    }
    
    @PostPersist
    public void postPersist(Account account) {
        // Publish account created event
    }
    
    @PreUpdate
    public void preUpdate(Account account) {
        // Custom pre-update logic
    }
    
    @PostUpdate
    public void postUpdate(Account account) {
        // Publish account updated event
    }
}
