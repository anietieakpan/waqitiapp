package com.waqiti.common.database.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * Base repository interface with built-in N+1 query prevention
 */
@NoRepositoryBean
public interface OptimizedJpaRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {
    
    /**
     * Find by ID with entity graph to prevent N+1 queries
     */
    @EntityGraph(attributePaths = {})
    Optional<T> findByIdWithGraph(ID id, String... attributePaths);
    
    /**
     * Find all with entity graph to prevent N+1 queries
     */
    @EntityGraph(attributePaths = {})
    List<T> findAllWithGraph(String... attributePaths);
    
    /**
     * Find all with pagination and entity graph
     */
    @EntityGraph(attributePaths = {})
    Page<T> findAllWithGraph(Pageable pageable, String... attributePaths);
    
    /**
     * Find by specification with entity graph
     */
    @EntityGraph(attributePaths = {})
    List<T> findAllWithGraph(Specification<T> spec, String... attributePaths);
    
    /**
     * Find by specification with pagination and entity graph
     */
    @EntityGraph(attributePaths = {})
    Page<T> findAllWithGraph(Specification<T> spec, Pageable pageable, String... attributePaths);
    
    /**
     * Batch load entities to prevent N+1 queries
     */
    List<T> findAllByIdInWithGraph(Iterable<ID> ids, String... attributePaths);
}