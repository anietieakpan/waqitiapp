package com.waqiti.common.database;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for analyzing query patterns
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryPatternAnalyzer {
    
    // Pattern cache for frequently analyzed queries
    private final Map<String, QueryPattern> patternCache = new ConcurrentHashMap<>();
    private final Map<String, PatternStatistics> patternStats = new ConcurrentHashMap<>();
    
    // SQL pattern matchers for advanced analysis
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "SELECT\\s+(.*?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+?))?", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern JOIN_PATTERN = Pattern.compile(
        "((?:INNER|LEFT|RIGHT|FULL)\\s+)?JOIN\\s+(\\w+)\\s+ON\\s+(.+?)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern INDEX_HINT_PATTERN = Pattern.compile(
        "USE\\s+INDEX\\s*\\(([^)]+)\\)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SUBQUERY_PATTERN = Pattern.compile(
        "\\(\\s*SELECT\\s+.*?\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    public List<QueryPattern> analyzePatterns(List<QueryExecution> executions) {
        log.debug("Analyzing {} query executions for patterns", executions.size());
        
        Map<String, List<QueryExecution>> groupedByPattern = new HashMap<>();
        
        // Group executions by normalized pattern
        for (QueryExecution execution : executions) {
            String pattern = extractPattern(execution.getQuery());
            groupedByPattern.computeIfAbsent(pattern, k -> new ArrayList<>()).add(execution);
        }
        
        List<QueryPattern> patterns = new ArrayList<>();
        
        // Analyze each pattern group
        for (Map.Entry<String, List<QueryExecution>> entry : groupedByPattern.entrySet()) {
            String patternKey = entry.getKey();
            List<QueryExecution> patternExecutions = entry.getValue();
            
            QueryPattern pattern = analyzePatternGroup(patternKey, patternExecutions);
            patterns.add(pattern);
            
            // Update statistics
            updatePatternStatistics(pattern, patternExecutions);
        }
        
        // Sort patterns by frequency and impact
        patterns.sort((p1, p2) -> {
            double impact1 = p1.getFrequency() * p1.getAverageExecutionTime();
            double impact2 = p2.getFrequency() * p2.getAverageExecutionTime();
            return Double.compare(impact2, impact1);
        });
        
        return patterns;
    }
    
    public QueryPattern identifyPattern(String query) {
        // Check cache first
        String normalizedQuery = normalizeQuery(query);
        
        return patternCache.computeIfAbsent(normalizedQuery, k -> {
            QueryPattern.QueryPatternBuilder patternBuilder = QueryPattern.builder()
                .patternId(generatePatternId(normalizedQuery))
                .sqlTemplate(normalizedQuery)
                .queryType(determineQueryType(query))
                .complexity(calculateQueryComplexity(query))
                .estimatedCost(estimateQueryCost(query))
                .isParameterized(isParameterized(query))
                .hasSubqueries(hasSubqueries(query));
            
            // Extract tables involved
            Set<String> tables = extractTables(query);
            patternBuilder.tablesInvolved(tables);
            
            // Extract columns used in WHERE clause
            Set<String> whereColumns = extractWhereColumns(query);
            patternBuilder.whereColumns(whereColumns);
            
            // Extract JOIN information
            List<JoinInfo> joins = extractJoins(query);
            patternBuilder.joins(joins);
            
            // Identify potential optimization opportunities
            List<OptimizationHint> hints = identifyOptimizationHints(query);
            patternBuilder.optimizationHints(hints);
            
            return patternBuilder.build();
        });
    }
    
    private String normalizeQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        
        String normalized = query;
        
        // Remove comments
        normalized = normalized.replaceAll("/\\*.*?\\*/", "");
        normalized = normalized.replaceAll("--.*?\\n", "\\n");
        
        // Normalize whitespace
        normalized = normalized.replaceAll("\\s+", " ").trim();
        
        // Replace string literals with placeholders
        normalized = normalized.replaceAll("'[^']*'", "?");
        normalized = normalized.replaceAll("\"[^\"]*\"", "?");
        
        // Replace numeric literals with placeholders
        normalized = normalized.replaceAll("\\b\\d+\\.?\\d*\\b", "?");
        
        // Replace IN clauses with normalized form
        normalized = normalized.replaceAll("IN\\s*\\([^)]+\\)", "IN (?)");
        
        // Normalize case for keywords
        normalized = normalizeKeywords(normalized);
        
        return normalized;
    }
    
    private String determineQueryType(String query) {
        String upperQuery = query.toUpperCase().trim();
        
        // Handle CTEs and complex queries
        if (upperQuery.startsWith("WITH")) {
            // Find the main query after CTE
            int mainQueryStart = upperQuery.indexOf("SELECT");
            if (mainQueryStart == -1) mainQueryStart = upperQuery.indexOf("INSERT");
            if (mainQueryStart == -1) mainQueryStart = upperQuery.indexOf("UPDATE");
            if (mainQueryStart == -1) mainQueryStart = upperQuery.indexOf("DELETE");
            
            if (mainQueryStart != -1) {
                upperQuery = upperQuery.substring(mainQueryStart);
            }
        }
        
        if (upperQuery.startsWith("SELECT")) {
            if (upperQuery.contains("FOR UPDATE")) return "SELECT_FOR_UPDATE";
            return "SELECT";
        }
        if (upperQuery.startsWith("INSERT")) return "INSERT";
        if (upperQuery.startsWith("UPDATE")) return "UPDATE";
        if (upperQuery.startsWith("DELETE")) return "DELETE";
        if (upperQuery.startsWith("MERGE")) return "MERGE";
        if (upperQuery.startsWith("CREATE")) return "DDL";
        if (upperQuery.startsWith("ALTER")) return "DDL";
        if (upperQuery.startsWith("DROP")) return "DDL";
        if (upperQuery.startsWith("TRUNCATE")) return "TRUNCATE";
        
        return "OTHER";
    }
    
    public String extractPattern(String sqlQuery) {
        return normalizeQuery(sqlQuery);
    }
    
    @Cacheable(value = "queryAnalysis", key = "#sqlQuery")
    public QueryAnalysis analyzeQuery(String sqlQuery) {
        QueryPattern pattern = identifyPattern(sqlQuery);
        
        QueryAnalysis.QueryAnalysisBuilder analysisBuilder = QueryAnalysis.builder()
            .queryPattern(pattern.getSqlTemplate())
            .complexity(pattern.getComplexity())
            .isOptimizable(isOptimizable(sqlQuery))
            .estimatedCost(pattern.getEstimatedCost())
            .executionPlan(generateExecutionPlan(sqlQuery));
        
        // Add tables accessed
        analysisBuilder.tablesAccessed(new java.util.ArrayList<>(pattern.getTablesInvolved()));
        
        // Convert optimization hints to recommendations
        List<String> recommendations = generateRecommendations(pattern);
        
        // Create OptimizationRecommendation objects from strings
        List<OptimizationRecommendation> optimizationRecommendations = recommendations.stream()
            .map(rec -> OptimizationRecommendation.builder()
                .description(rec)
                .priority(OptimizationRecommendation.Priority.MEDIUM)
                .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                .expectedImprovement(50.0)
                .build())
            .collect(java.util.stream.Collectors.toList());
        
        analysisBuilder.recommendations(optimizationRecommendations);
        
        return analysisBuilder.build();
    }
    
    private boolean isOptimizable(String sql) {
        if (sql == null || sql.isEmpty()) {
            return false;
        }
        
        String upperSql = sql.toUpperCase();
        
        // Check for optimization opportunities
        return upperSql.contains("SELECT *") ||
               upperSql.contains("LIKE '%") ||
               upperSql.contains("OR") ||
               upperSql.contains("IN (SELECT") ||
               upperSql.contains("NOT IN") ||
               upperSql.contains("NOT EXISTS") ||
               upperSql.contains("DISTINCT") ||
               upperSql.contains("GROUP BY") ||
               upperSql.contains("ORDER BY") ||
               upperSql.contains("HAVING") ||
               hasMultipleJoins(upperSql) ||
               hasComplexSubqueries(upperSql) ||
               hasMissingIndexOpportunity(upperSql);
    }
    
    // Advanced analysis methods
    
    private QueryAnalysis.QueryComplexity calculateQueryComplexity(String query) {
        int score = 0;
        String upperQuery = query.toUpperCase();
        
        // Count joins
        int joinCount = countOccurrences(upperQuery, "JOIN");
        score += joinCount * 10;
        
        // Count subqueries
        int subqueryCount = countSubqueries(query);
        score += subqueryCount * 15;
        
        // Check for complex operations
        if (upperQuery.contains("GROUP BY")) score += 10;
        if (upperQuery.contains("HAVING")) score += 10;
        if (upperQuery.contains("DISTINCT")) score += 5;
        if (upperQuery.contains("UNION")) score += 15;
        if (upperQuery.contains("INTERSECT")) score += 15;
        if (upperQuery.contains("EXCEPT")) score += 15;
        
        // Check for window functions
        if (upperQuery.contains("OVER (")) score += 20;
        
        // Check for CTEs
        if (upperQuery.startsWith("WITH")) score += 10;
        
        // Determine complexity level
        if (score <= 10) return QueryAnalysis.QueryComplexity.SIMPLE;
        if (score <= 30) return QueryAnalysis.QueryComplexity.MODERATE;
        if (score <= 60) return QueryAnalysis.QueryComplexity.COMPLEX;
        return QueryAnalysis.QueryComplexity.VERY_COMPLEX;
    }
    
    private double estimateQueryCost(String query) {
        double baseCost = 1.0;
        String upperQuery = query.toUpperCase();
        
        // Estimate based on operations
        if (upperQuery.contains("SELECT *")) baseCost *= 1.5;
        
        int joinCount = countOccurrences(upperQuery, "JOIN");
        baseCost *= Math.pow(2, joinCount); // Exponential cost for joins
        
        if (upperQuery.contains("GROUP BY")) baseCost *= 2.0;
        if (upperQuery.contains("ORDER BY")) baseCost *= 1.5;
        if (upperQuery.contains("DISTINCT")) baseCost *= 1.8;
        
        int subqueryCount = countSubqueries(query);
        baseCost *= Math.pow(1.5, subqueryCount);
        
        // Check for full table scans
        if (!upperQuery.contains("WHERE") && upperQuery.contains("FROM")) {
            baseCost *= 3.0; // Likely full table scan
        }
        
        return baseCost;
    }
    
    private Set<String> extractTables(String query) {
        Set<String> tables = new HashSet<>();
        String upperQuery = query.toUpperCase();
        
        // Extract from FROM clause
        Pattern fromPattern = Pattern.compile("FROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher fromMatcher = fromPattern.matcher(query);
        while (fromMatcher.find()) {
            tables.add(fromMatcher.group(1).toLowerCase());
        }
        
        // Extract from JOIN clauses
        Matcher joinMatcher = JOIN_PATTERN.matcher(query);
        while (joinMatcher.find()) {
            tables.add(joinMatcher.group(2).toLowerCase());
        }
        
        return tables;
    }
    
    private Set<String> extractWhereColumns(String query) {
        Set<String> columns = new HashSet<>();
        
        Pattern wherePattern = Pattern.compile("WHERE\\s+(.+?)(?:GROUP|ORDER|LIMIT|$)", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher whereMatcher = wherePattern.matcher(query);
        
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1);
            
            // Extract column names from WHERE clause
            Pattern columnPattern = Pattern.compile("(\\w+)\\s*(?:=|<|>|LIKE|IN|BETWEEN)", 
                Pattern.CASE_INSENSITIVE);
            Matcher columnMatcher = columnPattern.matcher(whereClause);
            
            while (columnMatcher.find()) {
                String column = columnMatcher.group(1);
                if (!isKeyword(column)) {
                    columns.add(column.toLowerCase());
                }
            }
        }
        
        return columns;
    }
    
    private List<JoinInfo> extractJoins(String query) {
        List<JoinInfo> joins = new ArrayList<>();
        
        Matcher joinMatcher = JOIN_PATTERN.matcher(query);
        while (joinMatcher.find()) {
            String joinType = joinMatcher.group(1) != null ? 
                joinMatcher.group(1).trim() : "INNER";
            String table = joinMatcher.group(2);
            String condition = joinMatcher.group(3);
            
            JoinInfo.JoinType joinTypeEnum;
            try {
                joinTypeEnum = JoinInfo.JoinType.valueOf(joinType.toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException e) {
                joinTypeEnum = JoinInfo.JoinType.INNER;
            }
            
            joins.add(JoinInfo.builder()
                .joinType(joinTypeEnum)
                .rightTable(table)
                .joinCondition(condition)
                .build());
        }
        
        return joins;
    }
    
    private List<OptimizationHint> identifyOptimizationHints(String query) {
        List<OptimizationHint> hints = new ArrayList<>();
        String upperQuery = query.toUpperCase();
        
        // Check for SELECT *
        if (upperQuery.contains("SELECT *")) {
            hints.add(OptimizationHint.builder()
                .hintType("COLUMN_PROJECTION")
                .description("Avoid SELECT *, specify required columns")
                .priority(OptimizationHint.HintPriority.MEDIUM)
                .build());
        }
        
        // Check for missing WHERE clause
        if (upperQuery.contains("FROM") && !upperQuery.contains("WHERE")) {
            hints.add(OptimizationHint.builder()
                .hintType("MISSING_FILTER")
                .description("Query has no WHERE clause, may cause full table scan")
                .priority(OptimizationHint.HintPriority.HIGH)
                .build());
        }
        
        // Check for LIKE with leading wildcard
        if (upperQuery.matches(".*LIKE\\s+'%.*")) {
            hints.add(OptimizationHint.builder()
                .hintType("LEADING_WILDCARD")
                .description("Leading wildcard in LIKE prevents index usage")
                .priority(OptimizationHint.HintPriority.HIGH)
                .build());
        }
        
        // Check for OR conditions
        if (upperQuery.contains(" OR ")) {
            hints.add(OptimizationHint.builder()
                .hintType("OR_CONDITION")
                .description("OR conditions may prevent index usage, consider UNION")
                .priority(OptimizationHint.HintPriority.MEDIUM)
                .build());
        }
        
        // Check for NOT IN
        if (upperQuery.contains("NOT IN")) {
            hints.add(OptimizationHint.builder()
                .hintType("NOT_IN")
                .description("NOT IN can be slow, consider NOT EXISTS or LEFT JOIN")
                .priority(OptimizationHint.HintPriority.MEDIUM)
                .build());
        }
        
        return hints;
    }
    
    private void updatePatternStatistics(QueryPattern pattern, List<QueryExecution> executions) {
        String patternId = pattern.getPatternId();
        
        PatternStatistics stats = patternStats.computeIfAbsent(patternId, 
            k -> PatternStatistics.builder().patternId(k).build().init());
        
        // Update execution statistics
        for (QueryExecution execution : executions) {
            stats.updateWith(execution);
        }
        
        // Update pattern with latest statistics
        pattern.setFrequency(stats.getExecutionCount().get());
        pattern.setAverageExecutionTime(stats.getAverageExecutionTime());
        pattern.setMaxExecutionTime(stats.getMaxExecutionTime().get());
        pattern.setMinExecutionTime(stats.getMinExecutionTime().get());
    }
    
    private QueryPattern analyzePatternGroup(String patternKey, List<QueryExecution> executions) {
        QueryPattern pattern = identifyPattern(executions.get(0).getQuery());
        
        // Calculate statistics
        DoubleSummaryStatistics timeStats = executions.stream()
            .mapToDouble(QueryExecution::getExecutionTimeMs)
            .summaryStatistics();
        
        pattern.setFrequency(executions.size());
        pattern.setAverageExecutionTime(timeStats.getAverage());
        pattern.setMaxExecutionTime(timeStats.getMax());
        pattern.setMinExecutionTime(timeStats.getMin());
        
        // Calculate variance for anomaly detection
        double variance = calculateVariance(executions);
        pattern.setExecutionTimeVariance(variance);
        
        return pattern;
    }
    
    private double calculateVariance(List<QueryExecution> executions) {
        if (executions.size() < 2) return 0.0;
        
        double mean = executions.stream()
            .mapToDouble(QueryExecution::getExecutionTimeMs)
            .average().orElse(0.0);
        
        return executions.stream()
            .mapToDouble(e -> Math.pow(e.getExecutionTimeMs() - mean, 2))
            .average().orElse(0.0);
    }
    
    // Utility methods
    
    private String generatePatternId(String normalizedQuery) {
        return Integer.toHexString(normalizedQuery.hashCode());
    }
    
    private boolean isParameterized(String query) {
        return query.contains("?") || query.contains(":1") || query.contains("@p");
    }
    
    private boolean hasSubqueries(String query) {
        return SUBQUERY_PATTERN.matcher(query).find();
    }
    
    private int countSubqueries(String query) {
        Matcher matcher = SUBQUERY_PATTERN.matcher(query);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
    
    private int countOccurrences(String text, String pattern) {
        return (text.length() - text.replace(pattern, "").length()) / pattern.length();
    }
    
    private boolean hasMultipleJoins(String query) {
        return countOccurrences(query, "JOIN") > 2;
    }
    
    private boolean hasComplexSubqueries(String query) {
        return countSubqueries(query) > 1;
    }
    
    private boolean hasMissingIndexOpportunity(String query) {
        // Simple heuristic for missing index detection
        return query.toUpperCase().contains("WHERE") && 
               !query.toUpperCase().contains("USE INDEX");
    }
    
    private String normalizeKeywords(String query) {
        String[] keywords = {"SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", 
                           "FULL", "ON", "AND", "OR", "NOT", "IN", "EXISTS", "LIKE", 
                           "BETWEEN", "GROUP", "BY", "HAVING", "ORDER", "LIMIT", "OFFSET"};
        
        String normalized = query;
        for (String keyword : keywords) {
            normalized = normalized.replaceAll("\\b" + keyword + "\\b", keyword);
        }
        
        return normalized;
    }
    
    private boolean isKeyword(String word) {
        Set<String> keywords = Set.of("SELECT", "FROM", "WHERE", "AND", "OR", "NOT", 
                                     "NULL", "TRUE", "FALSE", "AS");
        return keywords.contains(word.toUpperCase());
    }
    
    private long estimateRowCount(String query) {
        // Simplified row count estimation
        String upperQuery = query.toUpperCase();
        
        if (!upperQuery.contains("WHERE")) {
            return 10000; // Assume large result set without WHERE
        }
        
        if (upperQuery.contains("LIMIT")) {
            Pattern limitPattern = Pattern.compile("LIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = limitPattern.matcher(query);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        }
        
        return 100; // Default estimate for filtered queries
    }
    
    private String generateExecutionPlan(String query) {
        // Simplified execution plan generation
        StringBuilder plan = new StringBuilder();
        
        String queryType = determineQueryType(query);
        plan.append("Query Type: ").append(queryType).append("\n");
        
        Set<String> tables = extractTables(query);
        plan.append("Tables: ").append(tables).append("\n");
        
        List<JoinInfo> joins = extractJoins(query);
        if (!joins.isEmpty()) {
            plan.append("Joins: ");
            joins.forEach(j -> plan.append(j.getJoinType()).append(" JOIN ").append(j.getRightTable()).append("; "));
            plan.append("\n");
        }
        
        return plan.toString();
    }
    
    private Map<String, Boolean> analyzeIndexUsage(String query) {
        Map<String, Boolean> indexUsage = new HashMap<>();
        
        Set<String> whereColumns = extractWhereColumns(query);
        for (String column : whereColumns) {
            // Simulate index check (in production, would query database metadata)
            indexUsage.put(column, shouldUseIndex(query, column));
        }
        
        return indexUsage;
    }
    
    private boolean shouldUseIndex(String query, String column) {
        String upperQuery = query.toUpperCase();
        
        // Check for conditions that prevent index usage
        if (upperQuery.contains("LIKE '%")) return false;
        if (upperQuery.contains(column.toUpperCase() + " IS NULL")) return false;
        if (upperQuery.contains("UPPER(" + column.toUpperCase() + ")")) return false;
        if (upperQuery.contains("LOWER(" + column.toUpperCase() + ")")) return false;
        
        return true;
    }
    
    private String analyzeJoinStrategy(String query) {
        List<JoinInfo> joins = extractJoins(query);
        
        if (joins.isEmpty()) return "NO_JOINS";
        if (joins.size() == 1) return "SINGLE_JOIN";
        
        // Analyze join types
        boolean hasOuterJoins = joins.stream()
            .anyMatch(j -> j.getJoinType().name().contains("LEFT") || 
                         j.getJoinType().name().contains("RIGHT") || 
                         j.getJoinType().name().contains("FULL"));
        
        if (hasOuterJoins) return "MIXED_JOINS";
        
        return "MULTIPLE_INNER_JOINS";
    }
    
    private List<String> analyzeSortOperations(String query) {
        List<String> sortOps = new ArrayList<>();
        
        Pattern orderByPattern = Pattern.compile("ORDER\\s+BY\\s+([^\\s,]+(?:\\s+(?:ASC|DESC))?(?:,\\s*[^\\s,]+(?:\\s+(?:ASC|DESC))?)*)", 
            Pattern.CASE_INSENSITIVE);
        Matcher matcher = orderByPattern.matcher(query);
        
        if (matcher.find()) {
            String orderByClause = matcher.group(1);
            String[] columns = orderByClause.split(",");
            
            for (String column : columns) {
                sortOps.add(column.trim());
            }
        }
        
        return sortOps;
    }
    
    private List<String> analyzeAggregations(String query) {
        List<String> aggregations = new ArrayList<>();
        
        String[] aggFunctions = {"COUNT", "SUM", "AVG", "MIN", "MAX", "GROUP_CONCAT", "ARRAY_AGG"};
        
        for (String func : aggFunctions) {
            Pattern pattern = Pattern.compile(func + "\\s*\\([^)]+\\)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query);
            
            while (matcher.find()) {
                aggregations.add(matcher.group());
            }
        }
        
        return aggregations;
    }
    
    private List<String> generateRecommendations(QueryPattern pattern) {
        List<String> recommendations = new ArrayList<>();
        
        for (OptimizationHint hint : pattern.getOptimizationHints()) {
            recommendations.add(hint.getDescription());
        }
        
        // Add pattern-specific recommendations
        if (pattern.getComplexity() == QueryAnalysis.QueryComplexity.VERY_COMPLEX) {
            recommendations.add("Consider breaking down this complex query into smaller parts");
        }
        
        if (pattern.getEstimatedCost() > 100) {
            recommendations.add("High cost query - consider caching results or materialized views");
        }
        
        if (pattern.getJoins() != null && pattern.getJoins().size() > 3) {
            recommendations.add("Multiple joins detected - verify join order and consider denormalization");
        }
        
        return recommendations;
    }
    
    // Scheduled cleanup of old pattern statistics
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void cleanupOldPatterns() {
        long threshold = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours
        
        patternStats.entrySet().removeIf(entry -> {
            java.time.Instant lastSeen = entry.getValue().getLastSeen();
            return lastSeen != null && lastSeen.toEpochMilli() < threshold;
        });
        
        log.info("Cleaned up old pattern statistics. Remaining patterns: {}", patternStats.size());
    }
    
    // Inner class for pattern statistics
    @lombok.Builder
    @lombok.Data
    private static class PatternStatistics {
        private String patternId;
        private final AtomicInteger executionCount = new AtomicInteger(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private final AtomicLong maxExecutionTime = new AtomicLong(0);
        private final AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong lastExecutionTime = new AtomicLong(0);
        @Builder.Default
        private volatile java.time.Instant lastSeen = java.time.Instant.now();
        
        public PatternStatistics init() {
            return this;
        }
        
        public void updateWith(QueryExecution execution) {
            recordExecution(execution.getExecutionTimeMs());
        }
        
        public void recordExecution(double executionTimeMs) {
            executionCount.incrementAndGet();
            long timeMs = (long) executionTimeMs;
            totalExecutionTime.addAndGet(timeMs);
            
            maxExecutionTime.updateAndGet(current -> Math.max(current, timeMs));
            minExecutionTime.updateAndGet(current -> Math.min(current, timeMs));
            lastExecutionTime.set(System.currentTimeMillis());
            lastSeen = java.time.Instant.now();
        }
        
        public AtomicInteger getExecutionCount() {
            return executionCount;
        }
        
        public double getAverageExecutionTime() {
            int count = executionCount.get();
            return count > 0 ? (double) totalExecutionTime.get() / count : 0;
        }
        
        public AtomicLong getMaxExecutionTime() {
            return maxExecutionTime;
        }
        
        public AtomicLong getMinExecutionTime() {
            return minExecutionTime;
        }
        
        public long getLastExecutionTime() {
            return lastExecutionTime.get();
        }
        
        public java.time.Instant getLastSeen() {
            return lastSeen;
        }
    }
}