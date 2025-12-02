package com.waqiti.common.repository;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Subgraph;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Optimized Repository Implementation to prevent N+1 queries
 * 
 * This implementation provides enhanced query optimization techniques including:
 * - Dynamic entity graphs for fetch optimization
 * - Batch fetching strategies
 * - Query result caching
 * - Projection support for reduced data transfer
 */
@Slf4j
@Transactional(readOnly = true)
public class OptimizedRepositoryImpl<T, ID extends Serializable> 
        extends SimpleJpaRepository<T, ID> implements OptimizedRepository<T, ID> {

    @PersistenceContext
    private EntityManager entityManager;
    
    private final Class<T> domainClass;

    public OptimizedRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.domainClass = entityInformation.getJavaType();
    }

    /**
     * Find all entities with optimized fetching to prevent N+1 queries
     */
    @Override
    public List<T> findAllOptimized(String... fetchAttributes) {
        log.debug("Finding all {} entities with optimized fetch: {}", domainClass.getSimpleName(), Arrays.toString(fetchAttributes));
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(domainClass);
        Root<T> root = query.from(domainClass);
        
        // Apply fetch joins for specified attributes
        for (String attribute : fetchAttributes) {
            if (attribute.contains(".")) {
                // Handle nested fetches
                String[] parts = attribute.split("\\.");
                Fetch<T, ?> fetch = root.fetch(parts[0], JoinType.LEFT);
                for (int i = 1; i < parts.length; i++) {
                    fetch = fetch.fetch(parts[i], JoinType.LEFT);
                }
            } else {
                root.fetch(attribute, JoinType.LEFT);
            }
        }
        
        query.select(root).distinct(true);
        
        return entityManager.createQuery(query).getResultList();
    }

    /**
     * Find entities by specification with optimized fetching
     */
    @Override
    public Page<T> findAllOptimized(Specification<T> spec, Pageable pageable, String... fetchAttributes) {
        log.debug("Finding {} entities with specification and optimized fetch", domainClass.getSimpleName());
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(domainClass);
        Root<T> root = query.from(domainClass);
        
        // Apply specification predicate
        Predicate predicate = spec != null ? spec.toPredicate(root, query, cb) : null;
        if (predicate != null) {
            query.where(predicate);
        }
        
        // Apply fetch joins
        Set<String> fetchedPaths = new HashSet<>();
        for (String attribute : fetchAttributes) {
            applyFetchJoin(root, attribute, fetchedPaths);
        }
        
        query.select(root).distinct(true);
        
        // Apply sorting
        if (pageable.getSort().isSorted()) {
            query.orderBy(toOrders(pageable.getSort(), root, cb));
        }
        
        // Execute query with pagination
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<T> results = typedQuery.getResultList();
        
        // Get total count
        Long total = getCount(spec);
        
        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Find entities using Entity Graph for eager loading
     */
    @Override
    public List<T> findWithEntityGraph(String graphName, Map<String, Object> hints) {
        log.debug("Finding {} entities using entity graph: {}", domainClass.getSimpleName(), graphName);
        
        EntityGraph<?> entityGraph = entityManager.getEntityGraph(graphName);
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(domainClass);
        Root<T> root = query.from(domainClass);
        query.select(root);
        
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        typedQuery.setHint("jakarta.persistence.fetchgraph", entityGraph);
        
        // Apply additional hints
        if (hints != null) {
            hints.forEach(typedQuery::setHint);
        }
        
        return typedQuery.getResultList();
    }

    /**
     * Find entities with dynamic entity graph
     */
    @Override
    public List<T> findWithDynamicGraph(Map<String, List<String>> fetchPaths) {
        log.debug("Finding {} entities with dynamic entity graph", domainClass.getSimpleName());
        
        EntityGraph<T> graph = entityManager.createEntityGraph(domainClass);
        
        // Build dynamic graph
        fetchPaths.forEach((key, subPaths) -> {
            if (subPaths.isEmpty()) {
                graph.addAttributeNodes(key);
            } else {
                Subgraph<Object> subgraph = graph.addSubgraph(key);
                for (String subPath : subPaths) {
                    if (subPath.contains(".")) {
                        String[] parts = subPath.split("\\.", 2);
                        addNestedSubgraph(subgraph, parts[0], parts[1]);
                    } else {
                        subgraph.addAttributeNodes(subPath);
                    }
                }
            }
        });
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(domainClass);
        Root<T> root = query.from(domainClass);
        query.select(root);
        
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        typedQuery.setHint("jakarta.persistence.loadgraph", graph);
        
        return typedQuery.getResultList();
    }

    /**
     * Batch load entities by IDs to prevent N+1 queries
     */
    @Override
    public Map<ID, T> batchLoadByIds(Collection<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        
        log.debug("Batch loading {} entities for {} IDs", domainClass.getSimpleName(), ids.size());
        
        // Split into batches to avoid query size limits
        List<List<ID>> batches = partition(new ArrayList<>(ids), 1000);
        Map<ID, T> result = new HashMap<>();
        
        for (List<ID> batch : batches) {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> query = cb.createQuery(domainClass);
            Root<T> root = query.from(domainClass);
            
            query.where(root.get("id").in(batch));
            
            List<T> entities = entityManager.createQuery(query).getResultList();
            entities.forEach(entity -> {
                @SuppressWarnings("unchecked")
                ID entityId = (ID) entityManager.getEntityManagerFactory()
                        .getPersistenceUnitUtil()
                        .getIdentifier(entity);
                result.put(entityId, entity);
            });
        }
        
        return result;
    }

    /**
     * Find with projection to reduce data transfer
     */
    @Override
    public <P> List<P> findWithProjection(Class<P> projectionClass, Specification<T> spec) {
        log.debug("Finding {} projections of type {}", domainClass.getSimpleName(), projectionClass.getSimpleName());
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> query = cb.createQuery(projectionClass);
        Root<T> root = query.from(domainClass);
        
        // Apply specification
        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, query.cast(Object.class), cb);
            if (predicate != null) {
                query.where(predicate);
            }
        }
        
        // Build projection selection
        List<Selection<?>> selections = new ArrayList<>();
        Arrays.stream(projectionClass.getDeclaredFields()).forEach(field -> {
            String fieldName = field.getName();
            if (root.get(fieldName) != null) {
                selections.add(root.get(fieldName).alias(fieldName));
            }
        });
        
        query.multiselect(selections);
        
        return entityManager.createQuery(query).getResultList();
    }

    /**
     * Optimize count queries
     */
    @Override
    public Long getCount(Specification<T> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(domainClass);
        
        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, query, cb);
            if (predicate != null) {
                query.where(predicate);
            }
        }
        
        query.select(cb.count(root));
        
        return entityManager.createQuery(query).getSingleResult();
    }

    /**
     * Execute update query in batch
     */
    @Override
    @Transactional
    public int batchUpdate(String updateQuery, Map<String, Object> parameters) {
        log.debug("Executing batch update: {}", updateQuery);
        
        TypedQuery<T> query = entityManager.createQuery(updateQuery, domainClass);
        parameters.forEach(query::setParameter);
        
        return query.executeUpdate();
    }

    /**
     * Find with query hints for optimization
     */
    @Override
    public List<T> findWithHints(Map<String, Object> hints) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(domainClass);
        Root<T> root = query.from(domainClass);
        query.select(root);
        
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        
        // Apply query hints
        hints.forEach(typedQuery::setHint);
        
        // Common optimization hints
        typedQuery.setHint("org.hibernate.readOnly", true);
        typedQuery.setHint("org.hibernate.fetchSize", 100);
        typedQuery.setHint("org.hibernate.cacheable", true);
        
        return typedQuery.getResultList();
    }

    // ==================== HELPER METHODS ====================

    private void applyFetchJoin(Root<T> root, String attribute, Set<String> fetchedPaths) {
        if (fetchedPaths.contains(attribute)) {
            return; // Already fetched
        }
        
        if (attribute.contains(".")) {
            String[] parts = attribute.split("\\.");
            Join<?, ?> join = root.join(parts[0], JoinType.LEFT);
            
            for (int i = 1; i < parts.length; i++) {
                join = join.join(parts[i], JoinType.LEFT);
            }
        } else {
            root.fetch(attribute, JoinType.LEFT);
        }
        
        fetchedPaths.add(attribute);
    }

    private void addNestedSubgraph(Subgraph<Object> parentGraph, String attribute, String nestedPath) {
        Subgraph<Object> subgraph = parentGraph.addSubgraph(attribute);
        
        if (nestedPath.contains(".")) {
            String[] parts = nestedPath.split("\\.", 2);
            addNestedSubgraph(subgraph, parts[0], parts[1]);
        } else {
            subgraph.addAttributeNodes(nestedPath);
        }
    }

    private List<Order> toOrders(Sort sort, Root<T> root, CriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        
        for (Sort.Order order : sort) {
            if (order.isAscending()) {
                orders.add(cb.asc(root.get(order.getProperty())));
            } else {
                orders.add(cb.desc(root.get(order.getProperty())));
            }
        }
        
        return orders;
    }

    private <E> List<List<E>> partition(List<E> list, int size) {
        List<List<E>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}

/**
 * Interface for Optimized Repository operations
 */
interface OptimizedRepository<T, ID extends Serializable> {
    
    List<T> findAllOptimized(String... fetchAttributes);
    
    Page<T> findAllOptimized(Specification<T> spec, Pageable pageable, String... fetchAttributes);
    
    List<T> findWithEntityGraph(String graphName, Map<String, Object> hints);
    
    List<T> findWithDynamicGraph(Map<String, List<String>> fetchPaths);
    
    Map<ID, T> batchLoadByIds(Collection<ID> ids);
    
    <P> List<P> findWithProjection(Class<P> projectionClass, Specification<T> spec);
    
    Long getCount(Specification<T> spec);
    
    int batchUpdate(String updateQuery, Map<String, Object> parameters);
    
    List<T> findWithHints(Map<String, Object> hints);
}