package com.waqiti.common.service;

import com.waqiti.common.dto.request.PageRequestDTO;
import com.waqiti.common.dto.response.PageResponseDTO;
import com.waqiti.common.entity.BaseEntity;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.repository.BaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Base service providing common CRUD operations with transaction management
 * 
 * This abstract class provides:
 * - Consistent transaction boundaries
 * - Caching strategies
 * - Event publishing
 * - Audit logging
 * - Error handling
 * - Validation
 * 
 * @param <T> Entity type
 * @param <D> DTO type
 * @param <R> Repository type
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Transactional(readOnly = true)
public abstract class BaseService<T extends BaseEntity, D, R extends BaseRepository<T, UUID>> {
    
    protected final R repository;
    protected final ApplicationEventPublisher eventPublisher;
    
    protected BaseService(R repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Get cache name for this service
     */
    protected abstract String getCacheName();
    
    /**
     * Convert entity to DTO
     */
    protected abstract D toDto(T entity);
    
    /**
     * Convert DTO to entity
     */
    protected abstract T toEntity(D dto);
    
    /**
     * Get entity name for logging
     */
    protected abstract String getEntityName();
    
    /**
     * Validate entity before save
     */
    protected void validateEntity(T entity) {
        // Override in subclasses for custom validation
    }
    
    /**
     * Find entity by ID
     * 
     * @param id Entity ID
     * @return DTO representation
     * @throws ResourceNotFoundException if not found
     */
    @Cacheable(value = "#{@this.getCacheName()}", key = "#id", unless = "#result == null")
    public D findById(@NotNull UUID id) {
        log.debug("Finding {} by ID: {}", getEntityName(), id);
        
        T entity = repository.findByIdAndNotDeleted(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format("%s not found with ID: %s", getEntityName(), id)
            ));
        
        return toDto(entity);
    }
    
    /**
     * Find all entities with pagination
     * 
     * @param pageRequest Pagination parameters
     * @return Page of DTOs
     */
    public PageResponseDTO<D> findAll(@Valid PageRequestDTO pageRequest) {
        log.debug("Finding all {} with pagination: {}", getEntityName(), pageRequest);
        
        Pageable pageable = pageRequest.toPageable();
        Page<T> page = repository.findAllNotDeleted(pageable);
        
        return PageResponseDTO.from(page, this::toDto);
    }
    
    /**
     * Find all active entities
     * 
     * @param pageRequest Pagination parameters
     * @return Page of active DTOs
     */
    @Cacheable(value = "#{@this.getCacheName()}_active", key = "#pageRequest.hashCode()")
    public PageResponseDTO<D> findAllActive(@Valid PageRequestDTO pageRequest) {
        log.debug("Finding all active {} with pagination: {}", getEntityName(), pageRequest);
        
        Pageable pageable = pageRequest.toPageable();
        Page<T> page = repository.findAllActive(pageable);
        
        return PageResponseDTO.from(page, this::toDto);
    }
    
    /**
     * Search entities with specification
     * 
     * @param spec Search specification
     * @param pageRequest Pagination parameters
     * @return Page of matching DTOs
     */
    public PageResponseDTO<D> search(Specification<T> spec, @Valid PageRequestDTO pageRequest) {
        log.debug("Searching {} with specification", getEntityName());
        
        Pageable pageable = pageRequest.toPageable();
        Page<T> page = repository.findAll(spec, pageable);
        
        return PageResponseDTO.from(page, this::toDto);
    }
    
    /**
     * Create new entity
     * 
     * @param dto DTO to create
     * @return Created DTO
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CachePut(value = "#{@this.getCacheName()}", key = "#result.id")
    public D create(@Valid @NotNull D dto) {
        log.info("Creating new {}", getEntityName());
        
        T entity = toEntity(dto);
        validateEntity(entity);
        
        // Ensure it's a new entity
        entity.setId(null);
        entity.setVersion(null);
        
        T savedEntity = repository.save(entity);
        D savedDto = toDto(savedEntity);
        
        // Publish creation event
        publishEvent(new EntityCreatedEvent<>(savedEntity, savedDto));
        
        log.info("Created {} with ID: {}", getEntityName(), savedEntity.getId());
        return savedDto;
    }
    
    /**
     * Update existing entity
     * 
     * @param id Entity ID
     * @param dto Updated DTO
     * @return Updated DTO
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CachePut(value = "#{@this.getCacheName()}", key = "#id")
    public D update(@NotNull UUID id, @Valid @NotNull D dto) {
        log.info("Updating {} with ID: {}", getEntityName(), id);
        
        T existingEntity = repository.findByIdAndNotDeleted(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format("%s not found with ID: %s", getEntityName(), id)
            ));
        
        T updatedEntity = toEntity(dto);
        updatedEntity.setId(existingEntity.getId());
        updatedEntity.setVersion(existingEntity.getVersion());
        updatedEntity.setCreatedAt(existingEntity.getCreatedAt());
        updatedEntity.setCreatedBy(existingEntity.getCreatedBy());
        
        validateEntity(updatedEntity);
        
        T savedEntity = repository.save(updatedEntity);
        D savedDto = toDto(savedEntity);
        
        // Publish update event
        publishEvent(new EntityUpdatedEvent<>(existingEntity, savedEntity, savedDto));
        
        log.info("Updated {} with ID: {}", getEntityName(), id);
        return savedDto;
    }
    
    /**
     * Partial update of entity
     * 
     * @param id Entity ID
     * @param updates Map of field updates
     * @return Updated DTO
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CachePut(value = "#{@this.getCacheName()}", key = "#id")
    public D partialUpdate(@NotNull UUID id, @NotNull java.util.Map<String, Object> updates) {
        log.info("Partially updating {} with ID: {}", getEntityName(), id);
        
        T entity = repository.findByIdAndNotDeleted(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format("%s not found with ID: %s", getEntityName(), id)
            ));
        
        // Apply updates using reflection or mapper
        applyPartialUpdates(entity, updates);
        validateEntity(entity);
        
        T savedEntity = repository.save(entity);
        D savedDto = toDto(savedEntity);
        
        // Publish update event
        publishEvent(new EntityUpdatedEvent<>(entity, savedEntity, savedDto));
        
        log.info("Partially updated {} with ID: {}", getEntityName(), id);
        return savedDto;
    }
    
    /**
     * Delete entity (soft delete)
     * 
     * @param id Entity ID
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CacheEvict(value = "#{@this.getCacheName()}", key = "#id")
    public void delete(@NotNull UUID id) {
        log.info("Deleting {} with ID: {}", getEntityName(), id);
        
        T entity = repository.findByIdAndNotDeleted(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format("%s not found with ID: %s", getEntityName(), id)
            ));
        
        // Perform soft delete
        String deletedBy = getCurrentUser();
        int deleted = repository.softDelete(id, LocalDateTime.now(), deletedBy);
        
        if (deleted == 0) {
            throw new BusinessException(
                String.format("Failed to delete %s with ID: %s", getEntityName(), id)
            );
        }
        
        // Publish deletion event
        publishEvent(new EntityDeletedEvent<>(entity));
        
        log.info("Deleted {} with ID: {}", getEntityName(), id);
    }
    
    /**
     * Bulk delete entities
     * 
     * @param ids List of entity IDs
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CacheEvict(value = "#{@this.getCacheName()}", allEntries = true)
    public void deleteAll(@NotNull List<UUID> ids) {
        log.info("Bulk deleting {} entities", ids.size());
        
        String deletedBy = getCurrentUser();
        int deleted = repository.softDeleteAll(ids, LocalDateTime.now(), deletedBy);
        
        log.info("Deleted {} entities", deleted);
    }
    
    /**
     * Restore soft-deleted entity
     * 
     * @param id Entity ID
     * @return Restored DTO
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CachePut(value = "#{@this.getCacheName()}", key = "#id")
    public D restore(@NotNull UUID id) {
        log.info("Restoring {} with ID: {}", getEntityName(), id);
        
        int restored = repository.restore(id);
        
        if (restored == 0) {
            throw new BusinessException(
                String.format("Failed to restore %s with ID: %s", getEntityName(), id)
            );
        }
        
        T entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format("%s not found with ID: %s", getEntityName(), id)
            ));
        
        D dto = toDto(entity);
        
        // Publish restoration event
        publishEvent(new EntityRestoredEvent<>(entity, dto));
        
        log.info("Restored {} with ID: {}", getEntityName(), id);
        return dto;
    }
    
    /**
     * Activate entity
     * 
     * @param id Entity ID
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CacheEvict(value = "#{@this.getCacheName()}", key = "#id")
    public void activate(@NotNull UUID id) {
        log.info("Activating {} with ID: {}", getEntityName(), id);
        
        int activated = repository.activate(id);
        
        if (activated == 0) {
            throw new BusinessException(
                String.format("Failed to activate %s with ID: %s", getEntityName(), id)
            );
        }
        
        log.info("Activated {} with ID: {}", getEntityName(), id);
    }
    
    /**
     * Deactivate entity
     * 
     * @param id Entity ID
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CacheEvict(value = "#{@this.getCacheName()}", key = "#id")
    public void deactivate(@NotNull UUID id) {
        log.info("Deactivating {} with ID: {}", getEntityName(), id);
        
        int deactivated = repository.deactivate(id);
        
        if (deactivated == 0) {
            throw new BusinessException(
                String.format("Failed to deactivate %s with ID: %s", getEntityName(), id)
            );
        }
        
        log.info("Deactivated {} with ID: {}", getEntityName(), id);
    }
    
    /**
     * Check if entity exists
     * 
     * @param id Entity ID
     * @return true if exists and not deleted
     */
    @Cacheable(value = "#{@this.getCacheName()}_exists", key = "#id")
    public boolean exists(@NotNull UUID id) {
        return repository.existsAndNotDeleted(id);
    }
    
    /**
     * Count all entities
     * 
     * @return Total count
     */
    @Cacheable(value = "#{@this.getCacheName()}_count")
    public long count() {
        return repository.count();
    }
    
    /**
     * Count active entities
     * 
     * @return Active count
     */
    @Cacheable(value = "#{@this.getCacheName()}_count_active")
    public long countActive() {
        return repository.countActive();
    }
    
    /**
     * Apply partial updates to entity
     * Override in subclasses for custom logic
     */
    protected void applyPartialUpdates(T entity, java.util.Map<String, Object> updates) {
        // Default implementation using reflection
        updates.forEach((key, value) -> {
            try {
                java.lang.reflect.Field field = entity.getClass().getDeclaredField(key);
                field.setAccessible(true);
                field.set(entity, value);
            } catch (Exception e) {
                log.warn("Failed to update field: {}", key, e);
            }
        });
    }
    
    /**
     * Get current user for audit
     */
    protected String getCurrentUser() {
        // Get from security context
        return org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();
    }
    
    /**
     * Publish domain event
     */
    protected void publishEvent(Object event) {
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Domain events
     */
    public record EntityCreatedEvent<T, D>(T entity, D dto) {}
    public record EntityUpdatedEvent<T, D>(T oldEntity, T newEntity, D dto) {}
    public record EntityDeletedEvent<T>(T entity) {}
    public record EntityRestoredEvent<T, D>(T entity, D dto) {}
}