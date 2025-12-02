package com.waqiti.notification.repository;

import com.waqiti.notification.domain.NotificationTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    /**
     * Find template by code
     */
    Optional<NotificationTemplate> findByCode(String code);

    /**
     * Find templates by category
     */
    List<NotificationTemplate> findByCategory(String category);

    /**
     * Find enabled templates
     */
    List<NotificationTemplate> findByEnabledTrue();

    /**
     * Find enabled templates by category
     */
    List<NotificationTemplate> findByCategoryAndEnabledTrue(String category);

    /**
     * Check if a template exists by code
     */
    boolean existsByCode(String code);
    
    // ===== PAGINATION SUPPORT - PERFORMANCE OPTIMIZED =====
    
    /**
     * Find templates by category with pagination
     */
    Page<NotificationTemplate> findByCategory(String category, Pageable pageable);
    
    /**
     * Find templates by enabled status with pagination
     */
    Page<NotificationTemplate> findByEnabled(Boolean enabled, Pageable pageable);
    
    /**
     * Find templates by category and enabled status with pagination
     */
    Page<NotificationTemplate> findByCategoryAndEnabled(String category, Boolean enabled, Pageable pageable);
}