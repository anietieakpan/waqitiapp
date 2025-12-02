package com.waqiti.savings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for GoalTemplate entity operations.
 * Manages pre-defined savings goal templates for quick goal creation.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface GoalTemplateRepository extends JpaRepository<GoalTemplate, UUID> {

    /**
     * Find all active templates.
     * Returns templates available for user selection.
     *
     * @return list of active goal templates
     */
    @Query("SELECT gt FROM GoalTemplate gt WHERE gt.isActive = true ORDER BY gt.popularity DESC, gt.name ASC")
    List<GoalTemplate> findAllActiveTemplates();

    /**
     * Find templates by category.
     *
     * @param category the goal category
     * @return list of templates in specified category
     */
    @Query("SELECT gt FROM GoalTemplate gt WHERE gt.category = :category " +
           "AND gt.isActive = true " +
           "ORDER BY gt.popularity DESC")
    List<GoalTemplate> findByCategory(@Param("category") String category);

    /**
     * Find most popular templates.
     * Used for recommendations and featured templates.
     *
     * @param limit number of templates to return
     * @return list of popular templates
     */
    @Query("SELECT gt FROM GoalTemplate gt WHERE gt.isActive = true " +
           "ORDER BY gt.popularity DESC, gt.usageCount DESC")
    List<GoalTemplate> findMostPopular(org.springframework.data.domain.Pageable pageable);

    /**
     * Find recently added templates.
     *
     * @param limit number of templates to return
     * @return list of newest templates
     */
    @Query("SELECT gt FROM GoalTemplate gt WHERE gt.isActive = true " +
           "ORDER BY gt.createdAt DESC")
    List<GoalTemplate> findRecentlyAdded(org.springframework.data.domain.Pageable pageable);

    /**
     * Find template by name.
     *
     * @param name the template name
     * @return Optional containing template if found
     */
    Optional<GoalTemplate> findByName(String name);

    /**
     * Search templates by keyword.
     * Searches in name, description, and tags.
     *
     * @param keyword search term
     * @return list of matching templates
     */
    @Query("SELECT gt FROM GoalTemplate gt WHERE gt.isActive = true " +
           "AND (LOWER(gt.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(gt.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(gt.tags) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY gt.popularity DESC")
    List<GoalTemplate> searchTemplates(@Param("keyword") String keyword);

    /**
     * Increment usage count for a template.
     * Called when a user creates a goal from this template.
     *
     * @param templateId the template UUID
     */
    @Query("UPDATE GoalTemplate gt SET gt.usageCount = gt.usageCount + 1 WHERE gt.id = :templateId")
    void incrementUsageCount(@Param("templateId") UUID templateId);

    /**
     * Get template statistics.
     *
     * @return aggregated statistics
     */
    @Query("SELECT NEW map(" +
           "COUNT(gt) as totalTemplates, " +
           "SUM(CASE WHEN gt.isActive = true THEN 1 ELSE 0 END) as activeTemplates, " +
           "SUM(gt.usageCount) as totalUsages, " +
           "AVG(gt.popularity) as averagePopularity) " +
           "FROM GoalTemplate gt")
    Optional<java.util.Map<String, Object>> getTemplateStatistics();
}

/**
 * Entity class for goal templates.
 */
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "goal_templates", indexes = {
    @jakarta.persistence.Index(name = "idx_goal_templates_category", columnList = "category"),
    @jakarta.persistence.Index(name = "idx_goal_templates_popularity", columnList = "popularity"),
    @jakarta.persistence.Index(name = "idx_goal_templates_active", columnList = "is_active")
})
class GoalTemplate {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue
    private UUID id;

    @jakarta.persistence.Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @jakarta.persistence.Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @jakarta.persistence.Column(name = "category", nullable = false, length = 50)
    private String category; // VACATION, EMERGENCY, HOME, CAR, etc.

    @jakarta.persistence.Column(name = "suggested_target_amount", precision = 19, scale = 4)
    private java.math.BigDecimal suggestedTargetAmount;

    @jakarta.persistence.Column(name = "suggested_duration_months")
    private Integer suggestedDurationMonths;

    @jakarta.persistence.Column(name = "icon", length = 50)
    private String icon;

    @jakarta.persistence.Column(name = "color", length = 7)
    private String color;

    @jakarta.persistence.Column(name = "image_url", length = 500)
    private String imageUrl;

    @jakarta.persistence.Column(name = "tags", columnDefinition = "TEXT")
    private String tags; // Comma-separated tags

    @jakarta.persistence.Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @jakarta.persistence.Column(name = "popularity", nullable = false)
    private Integer popularity = 0; // 0-100 score

    @jakarta.persistence.Column(name = "usage_count", nullable = false)
    private Long usageCount = 0L;

    @jakarta.persistence.Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;

    @jakarta.persistence.Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public java.math.BigDecimal getSuggestedTargetAmount() { return suggestedTargetAmount; }
    public void setSuggestedTargetAmount(java.math.BigDecimal suggestedTargetAmount) { this.suggestedTargetAmount = suggestedTargetAmount; }

    public Integer getSuggestedDurationMonths() { return suggestedDurationMonths; }
    public void setSuggestedDurationMonths(Integer suggestedDurationMonths) { this.suggestedDurationMonths = suggestedDurationMonths; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getPopularity() { return popularity; }
    public void setPopularity(Integer popularity) { this.popularity = popularity; }

    public Long getUsageCount() { return usageCount; }
    public void setUsageCount(Long usageCount) { this.usageCount = usageCount; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
