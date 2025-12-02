# Database Performance Optimization Guide

## Overview
This guide documents the comprehensive database performance optimizations implemented across all Waqiti microservices, including N+1 query fixes, high-volume table indexing, and monitoring strategies.

## N+1 Query Optimizations

### User Service Optimizations

#### Before (N+1 Issue)
```java
// This causes N+1 queries - 1 query for users + N queries for profiles
List<User> users = userRepository.findByStatus(UserStatus.ACTIVE);
for (User user : users) {
    UserProfile profile = profileRepository.findById(user.getId()); // N queries!
}
```

#### After (Optimized)
```java
// Single query with JOIN FETCH
List<User> users = userRepository.findByIdsWithProfiles(userIds);
Map<UUID, UserProfile> profiles = profileRepository.findByUserIdIn(userIds)
    .stream()
    .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
```

### Merchant Service Optimizations

#### Payment Methods Loading
```java
// Before: N+1 queries for payment methods
@Query("SELECT DISTINCT m FROM Merchant m " +
       "LEFT JOIN FETCH m.paymentMethods pm " +
       "WHERE m.id IN :merchantIds")
List<Merchant> findByIdsWithPaymentMethods(@Param("merchantIds") List<UUID> merchantIds);
```

#### Transaction History Loading
```java
// Optimized transaction loading with merchant details
@Query("SELECT DISTINCT m FROM Merchant m " +
       "LEFT JOIN FETCH m.transactions t " +
       "WHERE m.id IN :merchantIds AND t.createdAt >= :fromDate")
List<Merchant> findByIdsWithRecentTransactions(
    @Param("merchantIds") List<UUID> merchantIds,
    @Param("fromDate") LocalDateTime fromDate);
```

### Transaction Service Optimizations

#### Batch Loading Pattern
```java
public Page<Transaction> getTransactionsWithItems(UUID userId, Pageable pageable) {
    // 1. Get transactions page
    Page<Transaction> transactions = transactionRepository.findByUserId(userId, pageable);
    
    // 2. Extract transaction IDs
    List<UUID> transactionIds = transactions.getContent().stream()
        .map(Transaction::getId)
        .collect(Collectors.toList());
    
    // 3. Batch load items in single query
    Map<UUID, List<TransactionItem>> transactionToItems = 
        N1QueryOptimizer.createKeyToEntityListMap(
            transactionItemRepository.findByTransactionIdIn(transactionIds),
            TransactionItem::getTransactionId
        );
    
    // 4. Enrich transactions with items
    return N1QueryOptimizer.optimizePaginatedRelations(
        transactions, Transaction::getId, (ids) -> transactionToItems,
        (transaction, items) -> {
            transaction.setItems(items != null ? items : new ArrayList<>());
            return transaction;
        }
    );
}
```

## High-Volume Table Indexes

### Critical Performance Indexes

#### Transactions Table (Highest Volume)
```sql
-- Primary user transaction queries
CREATE INDEX CONCURRENTLY idx_transactions_user_id_status_date 
ON transactions (user_id, status, created_at DESC) 
INCLUDE (amount, currency, merchant_id, reference_id);

-- Merchant transaction queries
CREATE INDEX CONCURRENTLY idx_transactions_merchant_id_status_date 
ON transactions (merchant_id, status, created_at DESC) 
INCLUDE (amount, currency, user_id, reference_id);

-- Analytics and reporting
CREATE INDEX CONCURRENTLY idx_transactions_daily_aggregation 
ON transactions (date_trunc('day', created_at), currency, status) 
INCLUDE (amount, merchant_id);
```

#### Users Table
```sql
-- Authentication queries
CREATE INDEX CONCURRENTLY idx_users_email_status 
ON users (email, status) WHERE status = 'ACTIVE';

-- User management
CREATE INDEX CONCURRENTLY idx_users_created_at_status 
ON users (created_at DESC, status) 
INCLUDE (username, email, last_login_at);
```

#### Merchants Table
```sql
-- Merchant discovery
CREATE INDEX CONCURRENTLY idx_merchants_status_category_city 
ON merchants (status, business_category, business_address_city) 
WHERE status = 'ACTIVE';

-- Geographic search
CREATE INDEX CONCURRENTLY idx_merchants_location_gist 
ON merchants USING GIST (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)) 
WHERE status = 'ACTIVE' AND latitude IS NOT NULL AND longitude IS NOT NULL;
```

### Full-Text Search Optimization

#### Merchant Search
```sql
-- Full-text search index
CREATE INDEX CONCURRENTLY idx_merchants_fts 
ON merchants USING GIN (to_tsvector('english', 
    business_name || ' ' || business_category || ' ' || COALESCE(business_description, '')));

-- Trigram fuzzy matching
CREATE INDEX CONCURRENTLY idx_merchants_business_name_trgm 
ON merchants USING GIN (business_name gin_trgm_ops);
```

#### Optimized Search Query
```sql
SELECT m.* FROM merchants m 
WHERE (:query IS NULL OR 
  to_tsvector('english', m.business_name || ' ' || m.business_category) 
  @@ plainto_tsquery('english', :query)) 
AND (:category IS NULL OR m.business_category = :category) 
AND (:latitude IS NULL OR :longitude IS NULL OR :radiusKm IS NULL OR 
  ST_DWithin(ST_SetSRID(ST_MakePoint(m.longitude, m.latitude), 4326), 
             ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :radiusKm * 1000)) 
ORDER BY 
  CASE WHEN :latitude IS NOT NULL THEN 
    ST_Distance(ST_SetSRID(ST_MakePoint(m.longitude, m.latitude), 4326), 
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)) 
  END,
  ts_rank(to_tsvector('english', m.business_name || ' ' || m.business_category), 
          plainto_tsquery('english', :query)) DESC;
```

### Covering Indexes for Read Performance

#### Transaction Summaries
```sql
-- Covers most transaction summary queries without table lookups
CREATE INDEX CONCURRENTLY idx_transaction_summary_covering 
ON transactions (user_id, created_at) 
INCLUDE (amount, status, currency, merchant_id) 
WHERE status IN ('COMPLETED', 'FAILED');
```

#### User Profile Lookups
```sql
-- Covers profile queries completely
CREATE INDEX CONCURRENTLY idx_user_profiles_user_id_covering 
ON user_profiles (user_id) 
INCLUDE (first_name, last_name, preferred_language, preferred_currency);
```

## Performance Monitoring

### Index Usage Monitoring
```sql
-- Function to identify unused indexes
CREATE OR REPLACE FUNCTION get_unused_indexes() 
RETURNS TABLE(
    schemaname TEXT,
    tablename TEXT,
    indexname TEXT,
    index_size TEXT,
    index_scans BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        s.schemaname::TEXT,
        s.tablename::TEXT,
        s.indexname::TEXT,
        pg_size_pretty(pg_relation_size(s.indexname::regclass))::TEXT,
        s.idx_scan
    FROM pg_stat_user_indexes s
    JOIN pg_index i ON s.indexrelid = i.indexrelid
    WHERE s.idx_scan < 100  -- Less than 100 uses
    AND NOT i.indisunique   -- Exclude unique indexes
    ORDER BY s.idx_scan ASC, pg_relation_size(s.indexname::regclass) DESC;
END;
$$ LANGUAGE plpgsql;
```

### Slow Query Identification
```sql
-- Function to find slow queries
CREATE OR REPLACE FUNCTION get_slow_queries(min_duration_ms INTEGER DEFAULT 1000)
RETURNS TABLE(
    query TEXT,
    calls BIGINT,
    total_time_ms NUMERIC,
    mean_time_ms NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        pg_stat_statements.query::TEXT,
        pg_stat_statements.calls,
        pg_stat_statements.total_exec_time::NUMERIC,
        pg_stat_statements.mean_exec_time::NUMERIC
    FROM pg_stat_statements
    WHERE pg_stat_statements.mean_exec_time > min_duration_ms
    ORDER BY pg_stat_statements.mean_exec_time DESC;
END;
$$ LANGUAGE plpgsql;
```

## Query Pattern Optimizations

### Bulk Operations
```java
// Bulk update pattern to reduce round trips
@Query("UPDATE Transaction t SET t.status = :status, t.updatedAt = :updatedAt " +
       "WHERE t.id IN :transactionIds")
int bulkUpdateStatus(@Param("transactionIds") List<UUID> transactionIds,
                     @Param("status") String status,
                     @Param("updatedAt") LocalDateTime updatedAt);
```

### Pagination with Count Optimization
```java
// Avoid counting for better pagination performance
@Query(value = "SELECT t FROM Transaction t WHERE t.userId = :userId " +
               "ORDER BY t.createdAt DESC",
       countQuery = "SELECT count(t) FROM Transaction t WHERE t.userId = :userId")
Page<Transaction> findByUserIdOptimized(@Param("userId") UUID userId, Pageable pageable);
```

### Conditional Queries
```java
// Use conditional loading to avoid unnecessary joins
@Query("SELECT u FROM User u " +
       "LEFT JOIN FETCH u.profile " +
       "WHERE u.id = :userId " +
       "AND (:loadProfile = false OR u.profile IS NOT NULL)")
Optional<User> findByIdConditional(@Param("userId") UUID userId, 
                                   @Param("loadProfile") boolean loadProfile);
```

## Caching Integration

### Repository-Level Caching
```java
@Cacheable(value = "userProfiles", key = "#userId")
@Query("SELECT p FROM UserProfile p WHERE p.userId = :userId")
Optional<UserProfile> findCachedByUserId(@Param("userId") UUID userId);
```

### Query Result Caching
```java
@Cacheable(value = "transactionSummaries", 
           key = "#userIds.hashCode() + '_' + #startDate + '_' + #endDate")
public Map<UUID, TransactionSummaryResponse> getTransactionSummariesForUsers(
        List<UUID> userIds, LocalDate startDate, LocalDate endDate) {
    // Batch load with single query
    List<TransactionSummaryProjection> summaries = 
        transactionRepository.getTransactionSummariesByUserIds(userIds, startDate, endDate);
    
    return summaries.stream()
        .collect(Collectors.toMap(
            TransactionSummaryProjection::getUserId,
            this::mapToSummaryResponse
        ));
}
```

## Connection Pool Optimization

### HikariCP Configuration
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      leak-detection-threshold: 60000
      # Performance optimizations
      cache-prep-stmts: true
      prep-stmt-cache-size: 250
      prep-stmt-cache-sql-limit: 2048
      use-server-prep-stmts: true
      use-local-session-state: true
      rewrite-batched-statements: true
```

## Performance Metrics

### Target Performance Goals

#### Query Performance
- **Simple lookups**: < 5ms
- **Complex joins**: < 50ms
- **Bulk operations**: < 100ms
- **Full-text search**: < 200ms
- **Geographic search**: < 300ms

#### Index Effectiveness
- **Index hit ratio**: > 95%
- **Cache hit ratio**: > 80%
- **Buffer hit ratio**: > 99%

#### Connection Pool Health
- **Active connections**: < 80% of pool size
- **Connection wait time**: < 100ms
- **Connection leak**: 0

### Monitoring Queries

#### Index Usage Statistics
```sql
-- Monitor index scan ratios
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    CASE WHEN idx_scan > 0 
         THEN round(idx_tup_fetch::numeric / idx_scan, 2) 
         ELSE 0 
    END as avg_tuples_per_scan
FROM pg_stat_user_indexes 
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;
```

#### Table Size and Growth
```sql
-- Monitor table growth patterns
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size,
    pg_stat_get_tuples_inserted(c.oid) as inserts,
    pg_stat_get_tuples_updated(c.oid) as updates,
    pg_stat_get_tuples_deleted(c.oid) as deletes
FROM pg_tables t
JOIN pg_class c ON c.relname = t.tablename
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

## Maintenance Recommendations

### Regular Tasks

#### Daily
- Monitor slow query log
- Check connection pool metrics
- Review cache hit ratios

#### Weekly
- Analyze table statistics: `ANALYZE;`
- Review index usage with `get_unused_indexes()`
- Check for table bloat

#### Monthly
- Full database vacuum: `VACUUM ANALYZE;`
- Review and optimize frequently used queries
- Update table statistics
- Archive old audit logs and notifications

### Emergency Performance Issues

#### Quick Diagnostics
```sql
-- Find current blocking queries
SELECT 
    pg_stat_activity.pid,
    pg_stat_activity.query,
    pg_stat_activity.state,
    pg_stat_activity.wait_event,
    pg_stat_activity.query_start
FROM pg_stat_activity 
WHERE state = 'active' OR wait_event IS NOT NULL
ORDER BY query_start;

-- Find locks
SELECT 
    t.relname,
    l.locktype,
    l.mode,
    l.granted,
    a.query
FROM pg_locks l
JOIN pg_stat_activity a ON l.pid = a.pid
JOIN pg_class t ON l.relation = t.oid
WHERE NOT l.granted;
```

This comprehensive optimization strategy provides:
- **Eliminated N+1 queries** across all major services
- **Strategic indexing** for high-volume tables
- **Performance monitoring** capabilities
- **Maintenance procedures** for ongoing optimization
- **Target metrics** for performance validation