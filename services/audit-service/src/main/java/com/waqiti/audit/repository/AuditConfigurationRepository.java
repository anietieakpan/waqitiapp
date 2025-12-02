package com.waqiti.audit.repository;

import com.waqiti.audit.domain.AuditConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for audit configuration management
 */
@Repository
public interface AuditConfigurationRepository extends JpaRepository<AuditConfiguration, UUID> {
    
    /**
     * Find configuration by name
     */
    Optional<AuditConfiguration> findByConfigurationName(String configurationName);
    
    /**
     * Find active configurations by type
     */
    List<AuditConfiguration> findByConfigurationTypeAndIsActiveTrue(String configurationType);
    
    /**
     * Find all active configurations
     */
    List<AuditConfiguration> findByIsActiveTrueOrderByPriorityAsc();
    
    /**
     * Find configurations by compliance framework
     */
    List<AuditConfiguration> findByComplianceFrameworkAndIsActiveTrue(String complianceFramework);
    
    /**
     * Find effective configurations
     */
    @Query("SELECT a FROM AuditConfiguration a WHERE a.isActive = true " +
           "AND (a.effectiveFrom IS NULL OR a.effectiveFrom <= :currentDate) " +
           "AND (a.effectiveUntil IS NULL OR a.effectiveUntil >= :currentDate) " +
           "ORDER BY a.priority ASC")
    List<AuditConfiguration> findEffectiveConfigurations(@Param("currentDate") LocalDateTime currentDate);
    
    /**
     * Find retention policies
     */
    @Query("SELECT a FROM AuditConfiguration a WHERE a.configurationType = 'RETENTION_POLICY' " +
           "AND a.isActive = true ORDER BY a.priority ASC")
    List<AuditConfiguration> findActiveRetentionPolicies();
    
    /**
     * Find alert rules
     */
    @Query("SELECT a FROM AuditConfiguration a WHERE a.configurationType = 'ALERT_RULE' " +
           "AND a.isActive = true AND a.alertEnabled = true")
    List<AuditConfiguration> findActiveAlertRules();
    
    /**
     * Find configurations requiring encryption
     */
    List<AuditConfiguration> findByEncryptionRequiredTrueAndIsActiveTrue();
    
    /**
     * Find configurations by audit level
     */
    List<AuditConfiguration> findByAuditLevelAndIsActiveTrue(String auditLevel);
    
    /**
     * Check if configuration name exists
     */
    boolean existsByConfigurationNameAndConfigurationIdNot(String configurationName, UUID configurationId);
}