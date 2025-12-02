package com.waqiti.notification.repository;

import com.waqiti.notification.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for EmailTemplate entities
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, String> {

    /**
     * Find template by ID and active status
     */
    Optional<EmailTemplate> findByIdAndActive(String id, boolean active);

    /**
     * Find template by name
     */
    Optional<EmailTemplate> findByName(String name);

    /**
     * Find template by name and active status
     */
    Optional<EmailTemplate> findByNameAndActive(String name, boolean active);

    /**
     * Find all active templates
     */
    List<EmailTemplate> findByActiveTrue();

    /**
     * Find templates by category
     */
    List<EmailTemplate> findByCategory(String category);

    /**
     * Find templates by category and active status
     */
    List<EmailTemplate> findByCategoryAndActive(String category, boolean active);

    /**
     * Find template by ID
     */
    @Query("SELECT t FROM EmailTemplate t WHERE t.id = :id")
    Optional<EmailTemplate> findById(@Param("id") String id);

    /**
     * Check if template exists by name
     */
    boolean existsByName(String name);
}
