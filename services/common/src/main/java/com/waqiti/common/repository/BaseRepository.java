package com.waqiti.common.repository;

import com.waqiti.common.entity.BaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base repository interface providing enhanced CRUD operations
 * 
 * This interface extends Spring Data JPA repositories with additional
 * functionality for:
 * - Soft delete operations
 * - Active/inactive entity management
 * - Audit queries
 * - Bulk operations
 * - Custom finders
 * 
 * @param <T> Entity type extending BaseEntity
 * @param <ID> Primary key type (typically UUID)
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity, ID> extends 
        JpaRepository<T, ID>, 
        JpaSpecificationExecutor<T> {
    
    /**
     * Find entity by ID excluding soft-deleted records
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.deleted = false")
    Optional<T> findByIdAndNotDeleted(@Param("id") ID id);
    
    /**
     * Find all non-deleted entities
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.deleted = false")
    List<T> findAllNotDeleted();
    
    /**
     * Find all non-deleted entities with pagination
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.deleted = false")
    Page<T> findAllNotDeleted(Pageable pageable);
    
    /**
     * Find all active entities
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.active = true AND e.deleted = false")
    List<T> findAllActive();
    
    /**
     * Find all active entities with pagination
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.active = true AND e.deleted = false")
    Page<T> findAllActive(Pageable pageable);
    
    /**
     * Find entities by business key
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.businessKey = :businessKey AND e.deleted = false")
    Optional<T> findByBusinessKey(@Param("businessKey") String businessKey);
    
    /**
     * Find entities created between dates
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.createdAt BETWEEN :startDate AND :endDate AND e.deleted = false")
    List<T> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                   @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find entities modified after a specific date
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.updatedAt > :date AND e.deleted = false")
    List<T> findModifiedAfter(@Param("date") LocalDateTime date);
    
    /**
     * Find entities by creator
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.createdBy = :createdBy AND e.deleted = false")
    List<T> findByCreatedBy(@Param("createdBy") String createdBy);
    
    /**
     * Find entities by tenant ID (for multi-tenancy)
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.tenantId = :tenantId AND e.deleted = false")
    List<T> findByTenantId(@Param("tenantId") String tenantId);
    
    /**
     * Soft delete an entity
     */
    @Modifying
    @Transactional
    @Query("UPDATE #{#entityName} e SET e.deleted = true, e.deletedAt = :deletedAt, " +
           "e.deletedBy = :deletedBy, e.active = false WHERE e.id = :id")
    int softDelete(@Param("id") ID id, 
                   @Param("deletedAt") LocalDateTime deletedAt, 
                   @Param("deletedBy") String deletedBy);
    
    /**
     * Soft delete multiple entities
     */
    @Modifying
    @Transactional
    @Query("UPDATE #{#entityName} e SET e.deleted = true, e.deletedAt = :deletedAt, " +
           "e.deletedBy = :deletedBy, e.active = false WHERE e.id IN :ids")
    int softDeleteAll(@Param("ids") List<ID> ids, 
                      @Param("deletedAt") LocalDateTime deletedAt, 
                      @Param("deletedBy") String deletedBy);
    
    /**
     * Restore a soft-deleted entity
     */
    @Modifying
    @Transactional
    @Query("UPDATE #{#entityName} e SET e.deleted = false, e.deletedAt = null, " +
           "e.deletedBy = null, e.active = true WHERE e.id = :id")
    int restore(@Param("id") ID id);
    
    /**
     * Activate an entity
     */
    @Modifying
    @Transactional
    @Query("UPDATE #{#entityName} e SET e.active = true WHERE e.id = :id")
    int activate(@Param("id") ID id);
    
    /**
     * Deactivate an entity
     */
    @Modifying
    @Transactional
    @Query("UPDATE #{#entityName} e SET e.active = false WHERE e.id = :id")
    int deactivate(@Param("id") ID id);
    
    /**
     * Bulk activate entities
     */
    @Modifying
    @Transactional
    @Query("UPDATE #{#entityName} e SET e.active = true WHERE e.id IN :ids")
    int activateAll(@Param("ids") List<ID> ids);
    
    /**
     * Bulk deactivate entities
     */
    @Modifying
    @Transactional
    @Query("UPDATE #{#entityName} e SET e.active = false WHERE e.id IN :ids")
    int deactivateAll(@Param("ids") List<ID> ids);
    
    /**
     * Count active entities
     */
    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.active = true AND e.deleted = false")
    long countActive();
    
    /**
     * Count deleted entities
     */
    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.deleted = true")
    long countDeleted();
    
    /**
     * Check if entity exists and is not deleted
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM #{#entityName} e " +
           "WHERE e.id = :id AND e.deleted = false")
    boolean existsAndNotDeleted(@Param("id") ID id);
    
    /**
     * Check if business key exists
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM #{#entityName} e " +
           "WHERE e.businessKey = :businessKey AND e.deleted = false")
    boolean existsByBusinessKey(@Param("businessKey") String businessKey);
    
    /**
     * Delete all entities older than specified date (hard delete)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM #{#entityName} e WHERE e.createdAt < :date")
    int deleteOlderThan(@Param("date") LocalDateTime date);
    
    /**
     * Find entities with metadata containing specific key
     */
    @Query(value = "SELECT * FROM #{#entityName} e WHERE e.metadata @> :key::jsonb AND e.deleted = false", 
           nativeQuery = true)
    List<T> findByMetadataKey(@Param("key") String key);
    
    /**
     * Update metadata for an entity
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE #{#entityName} SET metadata = metadata || :metadata::jsonb WHERE id = :id", 
           nativeQuery = true)
    int updateMetadata(@Param("id") UUID id, @Param("metadata") String metadata);
    
    /**
     * Find recently created entities
     */
    default List<T> findRecentlyCreated(int limit) {
        return findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }
    
    /**
     * Find recently modified entities
     */
    default List<T> findRecentlyModified(int limit) {
        return findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent();
    }
    
    /**
     * Refresh entity from database
     */
    @Transactional(readOnly = true)
    default Optional<T> refresh(T entity) {
        if (entity == null || entity.getId() == null) {
            return Optional.empty();
        }
        getEntityManager().detach(entity);
        return findById((ID) entity.getId());
    }
    
    /**
     * Get reference to EntityManager (to be implemented by concrete repositories)
     * This method should be implemented in concrete repository classes by injecting EntityManager
     */
    default jakarta.persistence.EntityManager getEntityManager() {
        throw new UnsupportedOperationException(
            "This method must be implemented by concrete repository classes with @PersistenceContext EntityManager"
        );
    }
}