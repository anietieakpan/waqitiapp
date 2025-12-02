package com.waqiti.analytics.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Query optimizer for transaction analytics
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionQueryOptimizer {
    
    private static final Set<String> INDEXED_FIELDS = Set.of(
        "customerId", "merchantId", "status", "type", "currency", 
        "createdAt", "amount", "country", "riskLevel"
    );
    
    private static final Map<String, String> FIELD_ALIASES = Map.of(
        "customer", "customerId",
        "merchant", "merchantId",
        "date", "createdAt",
        "value", "amount"
    );
    
    /**
     * Optimize query for performance
     */
    public OptimizedQuery optimizeQuery(Map<String, Object> filters, 
                                       String sortBy, 
                                       String sortOrder,
                                       Integer limit,
                                       Integer offset) {
        
        OptimizedQuery.OptimizedQueryBuilder builder = OptimizedQuery.builder();
        
        // Normalize and validate filters
        Map<String, Object> optimizedFilters = optimizeFilters(filters);
        builder.filters(optimizedFilters);
        
        // Optimize sorting
        String optimizedSortBy = optimizeSortField(sortBy);
        builder.sortBy(optimizedSortBy);
        builder.sortOrder(sortOrder != null ? sortOrder.toUpperCase() : "DESC");
        
        // Optimize pagination
        builder.limit(Math.min(limit != null ? limit : 100, 1000)); // Max 1000 records
        builder.offset(offset != null ? offset : 0);
        
        // Add query hints
        builder.hints(generateQueryHints(optimizedFilters, optimizedSortBy));
        
        // Calculate estimated cost
        builder.estimatedCost(calculateQueryCost(optimizedFilters, limit));
        
        return builder.build();
    }
    
    /**
     * Optimize filters for better index usage
     */
    private Map<String, Object> optimizeFilters(Map<String, Object> filters) {
        Map<String, Object> optimized = new HashMap<>();
        
        if (filters == null) {
            return optimized;
        }
        
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            
            // Normalize field names
            field = FIELD_ALIASES.getOrDefault(field, field);
            
            // Skip non-indexed fields for large queries
            if (!INDEXED_FIELDS.contains(field)) {
                log.warn("Filter on non-indexed field: {}", field);
            }
            
            // Optimize date ranges
            if ("createdAt".equals(field) && value instanceof String) {
                value = optimizeDateFilter((String) value);
            }
            
            // Optimize amount ranges
            if ("amount".equals(field) && value instanceof Map) {
                value = optimizeAmountFilter((Map<String, Object>) value);
            }
            
            optimized.put(field, value);
        }
        
        // Add default time filter if not present
        if (!optimized.containsKey("createdAt")) {
            // Default to last 30 days for performance
            Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
            optimized.put("createdAt", Map.of("gte", thirtyDaysAgo));
        }
        
        return optimized;
    }
    
    /**
     * Optimize sort field for index usage
     */
    private String optimizeSortField(String sortBy) {
        if (sortBy == null) {
            return "createdAt"; // Default to indexed timestamp
        }
        
        // Normalize field name
        sortBy = FIELD_ALIASES.getOrDefault(sortBy, sortBy);
        
        // Ensure field is indexed
        if (!INDEXED_FIELDS.contains(sortBy)) {
            log.warn("Sort on non-indexed field: {}, falling back to createdAt", sortBy);
            return "createdAt";
        }
        
        return sortBy;
    }
    
    /**
     * Generate query hints for database optimizer
     */
    private Map<String, String> generateQueryHints(Map<String, Object> filters, String sortBy) {
        Map<String, String> hints = new HashMap<>();
        
        // Index hints based on filters
        if (filters.containsKey("customerId")) {
            hints.put("use_index", "idx_customer_created");
        } else if (filters.containsKey("merchantId")) {
            hints.put("use_index", "idx_merchant_created");
        } else if (filters.containsKey("status")) {
            hints.put("use_index", "idx_status_created");
        } else {
            hints.put("use_index", "idx_created_at");
        }
        
        // Parallel execution hints
        if (filters.size() > 3) {
            hints.put("parallel", "4");
        }
        
        // Memory hints for large result sets
        hints.put("work_mem", "64MB");
        
        return hints;
    }
    
    /**
     * Calculate estimated query cost
     */
    private Double calculateQueryCost(Map<String, Object> filters, Integer limit) {
        double baseCost = 1.0;
        
        // Cost increases with number of filters
        baseCost += filters.size() * 0.5;
        
        // Cost decreases with indexed filters
        long indexedFilters = filters.keySet().stream()
            .mapToLong(field -> INDEXED_FIELDS.contains(field) ? 1 : 0)
            .sum();
        
        baseCost -= indexedFilters * 0.3;
        
        // Cost increases with result set size
        if (limit != null && limit > 100) {
            baseCost += Math.log10(limit);
        }
        
        // Time range cost
        if (filters.containsKey("createdAt")) {
            Object timeFilter = filters.get("createdAt");
            if (timeFilter instanceof Map) {
                Map<String, Object> range = (Map<String, Object>) timeFilter;
                if (range.containsKey("gte") && range.containsKey("lte")) {
                    // Narrow time range reduces cost
                    baseCost *= 0.8;
                }
            }
        }
        
        return Math.max(baseCost, 0.1);
    }
    
    /**
     * Optimize date filter
     */
    private Object optimizeDateFilter(String dateValue) {
        try {
            // Parse and normalize date
            Instant instant = Instant.parse(dateValue);
            return instant;
        } catch (Exception e) {
            log.warn("Invalid date filter: {}", dateValue);
            return dateValue;
        }
    }
    
    /**
     * Optimize amount filter
     */
    private Object optimizeAmountFilter(Map<String, Object> amountFilter) {
        Map<String, Object> optimized = new HashMap<>(amountFilter);
        
        // Ensure proper numeric types
        for (Map.Entry<String, Object> entry : optimized.entrySet()) {
            try {
                if (entry.getValue() instanceof String) {
                    entry.setValue(Double.parseDouble((String) entry.getValue()));
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid amount filter value: {}", entry.getValue());
            }
        }
        
        return optimized;
    }
}