package com.waqiti.corebanking.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base audit entity for core banking domain objects.
 * Provides common audit fields for all entities.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Data
public abstract class BaseAuditEntity {
    
    @CreatedBy
    @Column(name = "created_by", length = 50, updatable = false)
    protected String createdBy;
    
    @LastModifiedBy
    @Column(name = "updated_by", length = 50)
    protected String updatedBy;
}