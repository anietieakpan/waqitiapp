package com.waqiti.common.database.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Subgraph;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of optimized JPA repository with N+1 query prevention
 */
@Transactional(readOnly = true)
public class OptimizedJpaRepositoryImpl<T, ID extends Serializable> 
        extends SimpleJpaRepository<T, ID> 
        implements OptimizedJpaRepository<T, ID> {
    
    private final EntityManager entityManager;
    private final JpaEntityInformation<T, ?> entityInformation;
    
    public OptimizedJpaRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, 
                                     EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.entityInformation = entityInformation;
    }
    
    @Override
    public Optional<T> findByIdWithGraph(ID id, String... attributePaths) {
        EntityGraph<T> graph = createEntityGraph(attributePaths);
        
        Map<String, Object> hints = new HashMap<>();
        hints.put("jakarta.persistence.fetchgraph", graph);
        
        return Optional.ofNullable(
            entityManager.find(getDomainClass(), id, hints)
        );
    }
    
    @Override
    public List<T> findAllWithGraph(String... attributePaths) {
        TypedQuery<T> query = getQuery(null, Sort.unsorted());
        applyEntityGraph(query, attributePaths);
        return query.getResultList();
    }
    
    @Override
    public Page<T> findAllWithGraph(Pageable pageable, String... attributePaths) {
        TypedQuery<T> query = getQuery(null, pageable);
        applyEntityGraph(query, attributePaths);
        
        return pageable.isUnpaged() ? 
            new PageImpl<>(query.getResultList()) :
            readPage(query, getDomainClass(), pageable, null);
    }
    
    @Override
    public List<T> findAllWithGraph(Specification<T> spec, String... attributePaths) {
        TypedQuery<T> query = getQuery(spec, Sort.unsorted());
        applyEntityGraph(query, attributePaths);
        return query.getResultList();
    }
    
    @Override
    public Page<T> findAllWithGraph(Specification<T> spec, Pageable pageable, String... attributePaths) {
        TypedQuery<T> query = getQuery(spec, pageable);
        applyEntityGraph(query, attributePaths);
        
        return pageable.isUnpaged() ? 
            new PageImpl<>(query.getResultList()) :
            readPage(query, getDomainClass(), pageable, spec);
    }
    
    @Override
    public List<T> findAllByIdInWithGraph(Iterable<ID> ids, String... attributePaths) {
        if (!ids.iterator().hasNext()) {
            return List.of();
        }
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getDomainClass());
        Root<T> root = query.from(getDomainClass());
        
        query.select(root)
             .where(root.get(entityInformation.getIdAttribute()).in(ids));
        
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        applyEntityGraph(typedQuery, attributePaths);
        
        return typedQuery.getResultList();
    }
    
    private EntityGraph<T> createEntityGraph(String... attributePaths) {
        EntityGraph<T> graph = entityManager.createEntityGraph(getDomainClass());
        
        for (String path : attributePaths) {
            String[] parts = path.split("\\.");
            if (parts.length == 1) {
                graph.addAttributeNodes(path);
            } else {
                // Handle nested paths
                addSubgraph(graph, parts);
            }
        }
        
        return graph;
    }
    
    private void addSubgraph(EntityGraph<T> graph, String[] pathParts) {
        EntityGraph<?> currentGraph = graph;
        
        for (int i = 0; i < pathParts.length; i++) {
            if (i == pathParts.length - 1) {
                currentGraph.addAttributeNodes(pathParts[i]);
            } else {
                Subgraph<?> subgraph = currentGraph.addSubgraph(pathParts[i]);
                currentGraph = (EntityGraph<?>) subgraph;
            }
        }
    }
    
    private void applyEntityGraph(TypedQuery<T> query, String... attributePaths) {
        if (attributePaths.length > 0) {
            EntityGraph<T> graph = createEntityGraph(attributePaths);
            query.setHint("jakarta.persistence.fetchgraph", graph);
        }
    }
}