package com.waqiti.common.optimization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for optimizing N+1 query scenarios
 */
@Slf4j
@Component
public class N1QueryOptimizer {

    /**
     * Generic method to batch load related entities to avoid N+1 queries
     * 
     * @param parentEntities The parent entities
     * @param keyExtractor Function to extract the key from parent entity
     * @param batchLoader Function to batch load related entities by keys
     * @param relationMapper Function to map parent entity to related entities
     * @param <P> Parent entity type
     * @param <K> Key type
     * @param <R> Related entity type
     * @return Map of parent entities to their related entities
     */
    public static <P, K, R> Map<P, List<R>> batchLoadRelations(
            List<P> parentEntities,
            Function<P, K> keyExtractor,
            Function<List<K>, Map<K, List<R>>> batchLoader,
            Function<P, List<R>> relationMapper) {
        
        if (parentEntities.isEmpty()) {
            return new HashMap<>();
        }
        
        // Extract keys from parent entities
        List<K> keys = parentEntities.stream()
                .map(keyExtractor)
                .distinct()
                .collect(Collectors.toList());
        
        // Batch load related entities
        Map<K, List<R>> keyToRelations = batchLoader.apply(keys);
        
        // Map parent entities to their relations
        return parentEntities.stream()
                .collect(Collectors.toMap(
                    Function.identity(),
                    parent -> keyToRelations.getOrDefault(keyExtractor.apply(parent), new ArrayList<>())
                ));
    }

    /**
     * Optimize paginated results by batch loading relations
     */
    public static <P, K, R> Page<P> optimizePaginatedRelations(
            Page<P> parentPage,
            Function<P, K> keyExtractor,
            Function<List<K>, Map<K, List<R>>> batchLoader,
            BiFunction<P, List<R>, P> entityEnricher) {
        
        List<P> parentEntities = parentPage.getContent();
        
        if (parentEntities.isEmpty()) {
            return parentPage;
        }
        
        // Batch load relations
        Map<P, List<R>> entityToRelations = N1QueryOptimizer.<P, K, R>batchLoadRelations(
            parentEntities, keyExtractor, batchLoader, null);
        
        // Enrich entities with their relations
        List<P> enrichedEntities = parentEntities.stream()
                .map(entity -> entityEnricher.apply(entity, entityToRelations.get(entity)))
                .collect(Collectors.toList());
        
        return new PageImpl<>(enrichedEntities, parentPage.getPageable(), parentPage.getTotalElements());
    }

    /**
     * Create a batch key-to-entity map from a list of entities
     */
    public static <K, E> Map<K, E> createKeyToEntityMap(
            List<E> entities,
            Function<E, K> keyExtractor) {
        
        return entities.stream()
                .collect(Collectors.toMap(keyExtractor, Function.identity()));
    }

    /**
     * Create a batch key-to-entity-list map from a list of entities
     */
    public static <K, E> Map<K, List<E>> createKeyToEntityListMap(
            List<E> entities,
            Function<E, K> keyExtractor) {
        
        return entities.stream()
                .collect(Collectors.groupingBy(keyExtractor));
    }

    /**
     * Performance monitoring wrapper for batch operations
     */
    public static <T> T monitorBatchOperation(String operationName, int entityCount, 
            Function<Void, T> operation) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            T result = operation.apply(null);
            long duration = System.currentTimeMillis() - startTime;
            
            log.debug("Batch operation '{}' completed for {} entities in {}ms", 
                    operationName, entityCount, duration);
            
            if (duration > 1000) { // Log warning if operation takes more than 1 second
                log.warn("Slow batch operation '{}' took {}ms for {} entities", 
                        operationName, duration, entityCount);
            }
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Batch operation '{}' failed after {}ms for {} entities: {}", 
                    operationName, duration, entityCount, e.getMessage());
            throw e;
        }
    }
}