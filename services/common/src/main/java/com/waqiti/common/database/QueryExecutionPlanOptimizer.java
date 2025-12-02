package com.waqiti.common.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for optimizing query execution plans
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryExecutionPlanOptimizer {
    
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    
    public QueryExecutionPlanOptimizer(QueryPatternAnalyzer patternAnalyzer,
                                     JdbcTemplate jdbcTemplate,
                                     DataSource dataSource) {
        this.patternAnalyzer = patternAnalyzer;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }
    
    private final QueryPatternAnalyzer patternAnalyzer;
    
    // Cache for database metadata
    private final Map<String, TableMetadata> tableMetadataCache = new ConcurrentHashMap<>();
    private final Map<String, List<IndexMetadata>> indexMetadataCache = new ConcurrentHashMap<>();
    private final Map<String, QueryPlan> executionPlanCache = new ConcurrentHashMap<>();
    
    // Optimization rule engine
    private final List<OptimizationRule> optimizationRules = initializeOptimizationRules();
    
    public List<OptimizationRecommendation> optimizeQuery(String query, QueryContext context) {
        log.debug("Optimizing query: {}", query);
        
        List<OptimizationRecommendation> recommendations = new ArrayList<>();
        
        try {
            // Analyze query structure
            QueryAnalysis analysis = patternAnalyzer.analyzeQuery(query);
            
            // Get current execution plan
            QueryPlan currentPlan = getExecutionPlan(query, context);
            
            // Apply optimization rules
            for (OptimizationRule rule : optimizationRules) {
                if (rule.applies(query, analysis, currentPlan)) {
                    OptimizationRecommendation recommendation = rule.recommend(query, analysis, currentPlan);
                    if (recommendation != null) {
                        recommendations.add(recommendation);
                    }
                }
            }
            
            // Analyze index usage
            recommendations.addAll(analyzeIndexOptimizations(query, analysis, context));
            
            // Analyze join optimizations
            recommendations.addAll(analyzeJoinOptimizations(query, analysis, context));
            
            // Analyze query rewrite opportunities
            recommendations.addAll(getRewriteRecommendations(analysis));
            
            // Sort by priority and expected improvement
            recommendations.sort((r1, r2) -> {
                int priorityCompare = r2.getPriority().compareTo(r1.getPriority());
                if (priorityCompare != 0) return priorityCompare;
                return Double.compare(r2.getExpectedImprovement(), r1.getExpectedImprovement());
            });
            
        } catch (Exception e) {
            log.error("Error optimizing query", e);
        }
        
        return recommendations;
    }
    
    public QueryAnalysis analyzeQuery(String query) {
        // Implementation would analyze query complexity and characteristics
        return QueryAnalysis.builder()
            .queryPattern(query)
            .complexity(QueryAnalysis.QueryComplexity.MODERATE)
            .isOptimizable(true)
            .build();
    }
    
    public QueryPerformanceMetrics createMetricsWithStdDev(double stdDev, long executionTime) {
        // Create metrics with standard deviation
        return QueryPerformanceMetrics.builder()
                .executionTimeMs(executionTime)
                .averageExecutionTimeMs(executionTime)
                .build();
    }
    
    @Async
    public CompletableFuture<QueryOptimizationResult> analyzeAndOptimizePlan(QueryPattern pattern) {
        log.debug("Analyzing and optimizing plan for pattern: {}", pattern.getPatternId());
        
        return CompletableFuture.supplyAsync(() -> {
            QueryOptimizationResult result = new QueryOptimizationResult();
            result.setPatternId(pattern.getPatternId());
            result.setOriginalPattern(pattern);
            
            try {
                // Analyze current performance
                PerformanceAnalysis perfAnalysis = analyzePerformance(pattern);
                result.setPerformanceAnalysis(perfAnalysis);
                
                // Generate optimization strategies
                List<OptimizationStrategy> strategies = generateOptimizationStrategies(pattern, perfAnalysis);
                result.setStrategies(strategies);
                
                // Simulate optimization impact
                for (OptimizationStrategy strategy : strategies) {
                    double estimatedImprovement = simulateOptimization(pattern, strategy);
                    strategy.setEstimatedImprovement(estimatedImprovement);
                }
                
                // Select best strategy
                OptimizationStrategy bestStrategy = selectBestStrategy(strategies);
                result.setRecommendedStrategy(bestStrategy);
                
                // Generate implementation plan
                if (bestStrategy != null) {
                    ImplementationPlan plan = generateImplementationPlan(bestStrategy, pattern);
                    result.setImplementationPlan(plan);
                }
                
            } catch (Exception e) {
                log.error("Error analyzing pattern: {}", pattern.getPatternId(), e);
                result.setError(e.getMessage());
            }
            
            return result;
        });
    }
    
    public List<OptimizationRecommendation> getRewriteRecommendations(QueryAnalysis analysis) {
        List<OptimizationRecommendation> recommendations = new ArrayList<>();
        
        String queryPattern = analysis.getQueryPattern();
        String upperPattern = queryPattern.toUpperCase();
        
        // SELECT * optimization
        if (upperPattern.contains("SELECT *")) {
            recommendations.add(OptimizationRecommendation.builder()
                .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                .description("Replace SELECT * with specific column names to reduce I/O and network traffic")
                .priority(OptimizationRecommendation.Priority.MEDIUM)
                .expectedImprovement(15.0)
                .suggestedQuery(generateColumnSpecificQuery(queryPattern))
                .build());
        }
        
        // Leading wildcard optimization
        if (upperPattern.contains("LIKE '%")) {
            recommendations.add(OptimizationRecommendation.builder()
                .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                .description("Leading wildcard prevents index usage. Consider full-text search or reverse index")
                .priority(OptimizationRecommendation.Priority.HIGH)
                .expectedImprovement(40.0)
                .suggestedQuery(suggestFullTextAlternative(queryPattern))
                .build());
        }
        
        // OR condition optimization
        if (upperPattern.contains(" OR ") && !upperPattern.contains("UNION")) {
            String unionQuery = convertOrToUnion(queryPattern);
            if (unionQuery != null) {
                recommendations.add(OptimizationRecommendation.builder()
                    .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                    .description("Rewrite OR conditions as UNION for better index usage")
                    .priority(OptimizationRecommendation.Priority.MEDIUM)
                    .expectedImprovement(25.0)
                    .suggestedQuery(unionQuery)
                    .build());
            }
        }
        
        // NOT IN optimization
        if (upperPattern.contains("NOT IN")) {
            String notExistsQuery = convertNotInToNotExists(queryPattern);
            if (notExistsQuery != null) {
                recommendations.add(OptimizationRecommendation.builder()
                    .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                    .description("Replace NOT IN with NOT EXISTS for better null handling and performance")
                    .priority(OptimizationRecommendation.Priority.HIGH)
                    .expectedImprovement(30.0)
                    .suggestedQuery(notExistsQuery)
                    .build());
            }
        }
        
        // DISTINCT optimization
        if (upperPattern.contains("DISTINCT") && upperPattern.contains("JOIN")) {
            recommendations.add(OptimizationRecommendation.builder()
                .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                .description("DISTINCT with JOINs may indicate incorrect join conditions. Review join logic")
                .priority(OptimizationRecommendation.Priority.MEDIUM)
                .expectedImprovement(20.0)
                .build());
        }
        
        // Subquery optimization
        if (upperPattern.contains("SELECT") && upperPattern.contains("(SELECT")) {
            recommendations.add(OptimizationRecommendation.builder()
                .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                .description("Consider rewriting subqueries as JOINs or CTEs for better optimization")
                .priority(OptimizationRecommendation.Priority.MEDIUM)
                .expectedImprovement(25.0)
                .suggestedQuery(convertSubqueryToJoin(queryPattern))
                .build());
        }
        
        // EXISTS vs IN optimization
        if (upperPattern.contains("IN (SELECT")) {
            String existsQuery = convertInToExists(queryPattern);
            if (existsQuery != null) {
                recommendations.add(OptimizationRecommendation.builder()
                    .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                    .description("Replace IN (SELECT...) with EXISTS for better performance")
                    .priority(OptimizationRecommendation.Priority.MEDIUM)
                    .expectedImprovement(20.0)
                    .suggestedQuery(existsQuery)
                    .build());
            }
        }
        
        // COUNT(*) optimization
        if (upperPattern.contains("COUNT(*)") && !upperPattern.contains("WHERE")) {
            recommendations.add(OptimizationRecommendation.builder()
                .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                .description("COUNT(*) without WHERE may be slow. Consider using approximate counts or caching")
                .priority(OptimizationRecommendation.Priority.LOW)
                .expectedImprovement(10.0)
                .build());
        }
        
        // OFFSET pagination optimization
        if (upperPattern.contains("OFFSET") && upperPattern.contains("LIMIT")) {
            Pattern offsetPattern = Pattern.compile("OFFSET\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = offsetPattern.matcher(queryPattern);
            if (matcher.find()) {
                int offset = Integer.parseInt(matcher.group(1));
                if (offset > 1000) {
                    recommendations.add(OptimizationRecommendation.builder()
                        .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                        .description("Large OFFSET values are inefficient. Consider keyset pagination")
                        .priority(OptimizationRecommendation.Priority.HIGH)
                        .expectedImprovement(50.0)
                        .suggestedQuery(suggestKeysetPagination(queryPattern))
                        .build());
                }
            }
        }
        
        return recommendations;
    }
    
    // Advanced optimization methods
    
    private QueryPlan getExecutionPlan(String query, QueryContext context) {
        String cacheKey = query + "_" + context.hashCode();
        
        return executionPlanCache.computeIfAbsent(cacheKey, k -> {
            QueryPlan plan = new QueryPlan();
            
            try {
                if (jdbcTemplate != null) {
                    // Get actual execution plan from database
                    String explainQuery = "EXPLAIN " + query;
                    List<Map<String, Object>> explainResult = jdbcTemplate.queryForList(explainQuery);
                    plan.setPlanSteps(parseExplainOutput(explainResult));
                } else {
                    // Generate estimated plan
                    plan = generateEstimatedPlan(query, context);
                }
                
                plan.setQuery(query);
                plan.setEstimatedCost(calculatePlanCost(plan));
                
            } catch (Exception e) {
                log.debug("Could not get execution plan: {}", e.getMessage());
                plan = generateEstimatedPlan(query, context);
            }
            
            return plan;
        });
    }
    
    private QueryPlan generateEstimatedPlan(String query, QueryContext context) {
        QueryPlan plan = new QueryPlan();
        plan.setQuery(query);
        
        List<PlanStep> steps = new ArrayList<>();
        
        // Parse query to identify operations
        QueryAnalysis analysis = patternAnalyzer.analyzeQuery(query);
        
        // Add scan/seek step
        if (query.toUpperCase().contains("WHERE")) {
            steps.add(new PlanStep("INDEX_SEEK", "Using index for WHERE clause", 10.0));
        } else {
            steps.add(new PlanStep("TABLE_SCAN", "Full table scan", 100.0));
        }
        
        // Add join steps
        if (query.toUpperCase().contains("JOIN")) {
            steps.add(new PlanStep("NESTED_LOOP_JOIN", "Nested loop join", 50.0));
        }
        
        // Add sort step
        if (query.toUpperCase().contains("ORDER BY")) {
            steps.add(new PlanStep("SORT", "Sorting results", 30.0));
        }
        
        // Add aggregation step
        if (query.toUpperCase().contains("GROUP BY")) {
            steps.add(new PlanStep("HASH_AGGREGATE", "Hash aggregation", 40.0));
        }
        
        plan.setPlanSteps(steps);
        plan.setEstimatedCost(calculatePlanCost(plan));
        
        return plan;
    }
    
    private List<PlanStep> parseExplainOutput(List<Map<String, Object>> explainResult) {
        List<PlanStep> steps = new ArrayList<>();
        
        for (Map<String, Object> row : explainResult) {
            String operation = String.valueOf(row.getOrDefault("select_type", "SIMPLE"));
            String description = String.valueOf(row.getOrDefault("Extra", ""));
            double cost = parseDouble(row.getOrDefault("rows", "1"));
            
            steps.add(new PlanStep(operation, description, cost));
        }
        
        return steps;
    }
    
    private double calculatePlanCost(QueryPlan plan) {
        return plan.getPlanSteps().stream()
            .mapToDouble(PlanStep::getCost)
            .sum();
    }
    
    private List<OptimizationRecommendation> analyzeIndexOptimizations(
            String query, QueryAnalysis analysis, QueryContext context) {
        
        List<OptimizationRecommendation> recommendations = new ArrayList<>();
        
        // Get tables involved in the query
        Set<String> tables = extractTables(query);
        
        for (String table : tables) {
            // Get existing indexes
            List<IndexMetadata> indexes = getTableIndexes(table);
            
            // Get columns used in WHERE clause
            Set<String> whereColumns = extractWhereColumns(query);
            
            // Check for missing indexes
            for (String column : whereColumns) {
                if (!hasIndex(indexes, column)) {
                    recommendations.add(OptimizationRecommendation.builder()
                        .type(OptimizationRecommendation.OptimizationType.CREATE_INDEX)
                        .description(String.format("Create index on %s.%s for WHERE clause", table, column))
                        .priority(OptimizationRecommendation.Priority.HIGH)
                        .expectedImprovement(calculateIndexImprovement(table, column))
                        .suggestedQuery(String.format("CREATE INDEX idx_%s_%s ON %s(%s)", 
                            table, column, table, column))
                        .build());
                }
            }
            
            // Check for covering index opportunities
            Set<String> selectColumns = extractSelectColumns(query);
            if (!selectColumns.isEmpty() && !whereColumns.isEmpty()) {
                String coveringIndex = suggestCoveringIndex(table, whereColumns, selectColumns, indexes);
                if (coveringIndex != null) {
                    recommendations.add(OptimizationRecommendation.builder()
                        .type(OptimizationRecommendation.OptimizationType.CREATE_INDEX)
                        .description("Create covering index to avoid key lookups")
                        .priority(OptimizationRecommendation.Priority.MEDIUM)
                        .expectedImprovement(25.0)
                        .suggestedQuery(coveringIndex)
                        .build());
                }
            }
        }
        
        return recommendations;
    }
    
    private List<OptimizationRecommendation> analyzeJoinOptimizations(
            String query, QueryAnalysis analysis, QueryContext context) {
        
        List<OptimizationRecommendation> recommendations = new ArrayList<>();
        
        // Extract join information
        List<JoinInfo> joins = extractJoins(query);
        
        if (joins.size() > 1) {
            // Analyze join order
            JoinOrderAnalysis joinAnalysis = analyzeJoinOrder(joins, context);
            
            if (joinAnalysis.hasOptimalOrder()) {
                recommendations.add(OptimizationRecommendation.builder()
                    .type(OptimizationRecommendation.OptimizationType.REORDER_JOINS)
                    .description("Reorder joins for better performance")
                    .priority(OptimizationRecommendation.Priority.HIGH)
                    .expectedImprovement(joinAnalysis.getExpectedImprovement())
                    .suggestedQuery(joinAnalysis.getOptimizedQuery())
                    .build());
            }
            
            // Check for missing join indexes
            for (JoinInfo join : joins) {
                String joinColumn = extractJoinColumn(join.getCondition());
                if (joinColumn != null) {
                    List<IndexMetadata> indexes = getTableIndexes(join.getTable());
                    if (!hasIndex(indexes, joinColumn)) {
                        recommendations.add(OptimizationRecommendation.builder()
                            .type(OptimizationRecommendation.OptimizationType.CREATE_INDEX)
                            .description(String.format("Create index on %s.%s for JOIN", 
                                join.getTable(), joinColumn))
                            .priority(OptimizationRecommendation.Priority.HIGH)
                            .expectedImprovement(35.0)
                            .suggestedQuery(String.format("CREATE INDEX idx_%s_%s ON %s(%s)", 
                                join.getTable(), joinColumn, join.getTable(), joinColumn))
                            .build());
                    }
                }
            }
        }
        
        return recommendations;
    }
    
    private List<IndexMetadata> getTableIndexes(String tableName) {
        return indexMetadataCache.computeIfAbsent(tableName, table -> {
            List<IndexMetadata> indexes = new ArrayList<>();
            
            try {
                if (dataSource != null) {
                    try (Connection conn = dataSource.getConnection()) {
                        DatabaseMetaData metaData = conn.getMetaData();
                        ResultSet rs = metaData.getIndexInfo(null, null, table, false, false);
                        
                        while (rs.next()) {
                            IndexMetadata index = new IndexMetadata();
                            index.setIndexName(rs.getString("INDEX_NAME"));
                            index.setColumnName(rs.getString("COLUMN_NAME"));
                            index.setUnique(rs.getBoolean("NON_UNIQUE"));
                            index.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                            indexes.add(index);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Could not get index metadata for table {}: {}", table, e.getMessage());
            }
            
            return indexes;
        });
    }
    
    private boolean hasIndex(List<IndexMetadata> indexes, String column) {
        return indexes.stream()
            .anyMatch(idx -> idx.getColumnName() != null && 
                           idx.getColumnName().equalsIgnoreCase(column));
    }
    
    private double calculateIndexImprovement(String table, String column) {
        // Estimate improvement based on table size and selectivity
        // In production, would query actual statistics
        return 30.0; // Default estimate
    }
    
    private String suggestCoveringIndex(String table, Set<String> whereColumns, 
                                       Set<String> selectColumns, List<IndexMetadata> existingIndexes) {
        // Check if covering index would be beneficial
        Set<String> allColumns = new HashSet<>(whereColumns);
        allColumns.addAll(selectColumns);
        
        // Don't suggest if too many columns
        if (allColumns.size() > 5) {
            return null;
        }
        
        // Check if index already exists
        for (IndexMetadata index : existingIndexes) {
            if (allColumns.contains(index.getColumnName())) {
                return null; // Some coverage already exists
            }
        }
        
        // Generate covering index suggestion
        String indexColumns = whereColumns.stream()
            .collect(Collectors.joining(", "));
        
        if (!selectColumns.isEmpty()) {
            String includeColumns = selectColumns.stream()
                .filter(col -> !whereColumns.contains(col))
                .collect(Collectors.joining(", "));
            
            if (!includeColumns.isEmpty()) {
                indexColumns += ") INCLUDE (" + includeColumns;
            }
        }
        
        return String.format("CREATE INDEX idx_%s_covering ON %s(%s)", 
            table, table, indexColumns);
    }
    
    // Query rewrite methods
    
    private String generateColumnSpecificQuery(String query) {
        // In production, would analyze table schema to get column names
        return query.replace("SELECT *", "SELECT /* specify columns here */");
    }
    
    private String suggestFullTextAlternative(String query) {
        // Suggest full-text search alternative
        return "/* Consider using full-text search: MATCH(column) AGAINST('search term') */";
    }
    
    private String convertOrToUnion(String query) {
        // Simple OR to UNION conversion
        // In production, would use proper SQL parser
        if (!query.toUpperCase().contains(" OR ")) {
            return null;
        }
        
        return "/* Rewrite as UNION: " + query + " */";
    }
    
    private String convertNotInToNotExists(String query) {
        // Convert NOT IN to NOT EXISTS
        if (!query.toUpperCase().contains("NOT IN")) {
            return null;
        }
        
        return "/* Rewrite using NOT EXISTS for better null handling */";
    }
    
    private String convertSubqueryToJoin(String query) {
        // Convert correlated subquery to JOIN
        return "/* Consider rewriting subquery as JOIN */";
    }
    
    private String convertInToExists(String query) {
        // Convert IN (SELECT...) to EXISTS
        return "/* Rewrite using EXISTS for better performance */";
    }
    
    private String suggestKeysetPagination(String query) {
        // Suggest keyset pagination instead of OFFSET
        return "/* Use keyset pagination: WHERE id > last_seen_id LIMIT n */";
    }
    
    // Utility methods
    
    private Set<String> extractTables(String query) {
        // Delegate to pattern analyzer
        QueryAnalysis analysis = patternAnalyzer.analyzeQuery(query);
        return new HashSet<>(); // Placeholder
    }
    
    private Set<String> extractWhereColumns(String query) {
        // Extract columns from WHERE clause
        Set<String> columns = new HashSet<>();
        
        Pattern wherePattern = Pattern.compile("WHERE\\s+(.+?)(?:GROUP|ORDER|LIMIT|$)", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = wherePattern.matcher(query);
        
        if (matcher.find()) {
            String whereClause = matcher.group(1);
            Pattern columnPattern = Pattern.compile("(\\w+)\\s*(?:=|<|>|LIKE|IN|BETWEEN)", 
                Pattern.CASE_INSENSITIVE);
            Matcher columnMatcher = columnPattern.matcher(whereClause);
            
            while (columnMatcher.find()) {
                columns.add(columnMatcher.group(1));
            }
        }
        
        return columns;
    }
    
    private Set<String> extractSelectColumns(String query) {
        // Extract columns from SELECT clause
        Set<String> columns = new HashSet<>();
        
        Pattern selectPattern = Pattern.compile("SELECT\\s+(.+?)\\s+FROM", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = selectPattern.matcher(query);
        
        if (matcher.find()) {
            String selectClause = matcher.group(1);
            if (!selectClause.trim().equals("*")) {
                String[] columnList = selectClause.split(",");
                for (String col : columnList) {
                    columns.add(col.trim().split("\\s+")[0]);
                }
            }
        }
        
        return columns;
    }
    
    private List<JoinInfo> extractJoins(String query) {
        // Delegate to pattern analyzer for consistency
        return new ArrayList<>(); // Placeholder
    }
    
    private String extractJoinColumn(String condition) {
        // Extract column name from join condition
        Pattern pattern = Pattern.compile("(\\w+)\\s*=\\s*\\w+\\.(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(condition);
        
        if (matcher.find()) {
            return matcher.group(2);
        }
        
        log.debug("No condition value found in pattern");
        return ""; // Return empty string instead of null
    }
    
    private JoinOrderAnalysis analyzeJoinOrder(List<JoinInfo> joins, QueryContext context) {
        // Analyze join order for optimization
        JoinOrderAnalysis analysis = new JoinOrderAnalysis();
        
        // Simple heuristic: smaller tables first
        // In production, would use actual table statistics
        analysis.setHasOptimalOrder(false);
        analysis.setExpectedImprovement(0.0);
        
        return analysis;
    }
    
    private double parseDouble(Object value) {
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
    
    // Advanced optimization methods
    
    private PerformanceAnalysis analyzePerformance(QueryPattern pattern) {
        PerformanceAnalysis analysis = new PerformanceAnalysis();
        
        analysis.setAverageExecutionTime(pattern.getAverageExecutionTime());
        analysis.setMaxExecutionTime(pattern.getMaxExecutionTime());
        analysis.setMinExecutionTime(pattern.getMinExecutionTime());
        analysis.setExecutionCount(pattern.getFrequency());
        
        // Identify bottlenecks
        if (pattern.getAverageExecutionTime() > 1000) {
            analysis.addBottleneck("Slow query execution (> 1s average)");
        }
        
        if (pattern.getMaxExecutionTime() > pattern.getAverageExecutionTime() * 3) {
            analysis.addBottleneck("High execution time variance");
        }
        
        return analysis;
    }
    
    private List<OptimizationStrategy> generateOptimizationStrategies(
            QueryPattern pattern, PerformanceAnalysis perfAnalysis) {
        
        List<OptimizationStrategy> strategies = new ArrayList<>();
        
        // Index strategy
        strategies.add(OptimizationStrategy.builder()
            .name("Index Optimization")
            .description("Add missing indexes and optimize existing ones")
            .complexity("LOW")
            .risk("LOW")
            .build());
        
        // Query rewrite strategy
        strategies.add(OptimizationStrategy.builder()
            .name("Query Rewrite")
            .description("Rewrite query for better execution plan")
            .complexity("MEDIUM")
            .risk("MEDIUM")
            .build());
        
        // Denormalization strategy
        if (pattern.getJoins() != null && pattern.getJoins().size() > 3) {
            strategies.add(OptimizationStrategy.builder()
                .name("Denormalization")
                .description("Denormalize tables to reduce joins")
                .complexity("HIGH")
                .risk("HIGH")
                .build());
        }
        
        // Caching strategy
        if (pattern.getFrequency() > 100) {
            strategies.add(OptimizationStrategy.builder()
                .name("Result Caching")
                .description("Cache frequently accessed results")
                .complexity("MEDIUM")
                .risk("LOW")
                .build());
        }
        
        return strategies;
    }
    
    private double simulateOptimization(QueryPattern pattern, OptimizationStrategy strategy) {
        // Simulate the impact of applying the optimization
        double baseTime = pattern.getAverageExecutionTime();
        
        switch (strategy.getName()) {
            case "Index Optimization":
                return baseTime * 0.3; // Assume 70% improvement
                
            case "Query Rewrite":
                return baseTime * 0.4; // Assume 60% improvement
                
            case "Denormalization":
                return baseTime * 0.2; // Assume 80% improvement
                
            case "Result Caching":
                return baseTime * 0.05; // Assume 95% improvement for cache hits
                
            default:
                return baseTime * 0.5; // Default 50% improvement
        }
    }
    
    private OptimizationStrategy selectBestStrategy(List<OptimizationStrategy> strategies) {
        if (strategies.isEmpty()) {
            log.debug("No optimization strategies available");
            return createDefaultStrategy();
        }
        
        // Select strategy with best improvement-to-complexity ratio
        return strategies.stream()
            .max((s1, s2) -> {
                double ratio1 = s1.getEstimatedImprovement() / complexityScore(s1.getComplexity());
                double ratio2 = s2.getEstimatedImprovement() / complexityScore(s2.getComplexity());
                return Double.compare(ratio1, ratio2);
            })
            .orElse(null);
    }
    
    private double complexityScore(String complexity) {
        switch (complexity) {
            case "LOW": return 1.0;
            case "MEDIUM": return 2.0;
            case "HIGH": return 3.0;
            default: return 2.0;
        }
    }
    
    private OptimizationStrategy createDefaultStrategy() {
        return OptimizationStrategy.builder()
            .strategyId("DEFAULT_NO_OPTIMIZATION")
            .strategyType("NONE")
            .description("No optimization strategy available")
            .complexity("LOW")
            .expectedImprovement(0.0)
            .build();
    }
    
    private ImplementationPlan generateImplementationPlan(
            OptimizationStrategy strategy, QueryPattern pattern) {
        
        ImplementationPlan plan = ImplementationPlan.builder()
            .strategyId(strategy.getStrategyId())
            .description(strategy.getDescription())
            .build();
        
        List<ImplementationPlan.ImplementationStep> steps = new ArrayList<>();
        
        switch (strategy.getName()) {
            case "Index Optimization":
                steps.add(ImplementationPlan.ImplementationStep.builder()
                    .stepNumber(1)
                    .description("Analyze current indexes")
                    .command("ANALYZE TABLE statistics")
                    .type(ImplementationPlan.ImplementationStep.StepType.DDL)
                    .estimatedDurationMs(5 * 60 * 1000)
                    .canRollback(true)
                    .build());
                steps.add(ImplementationPlan.ImplementationStep.builder()
                    .stepNumber(2)
                    .description("Create missing indexes")
                    .command("CREATE INDEX statements")
                    .type(ImplementationPlan.ImplementationStep.StepType.INDEX_CREATE)
                    .estimatedDurationMs(30 * 60 * 1000)
                    .canRollback(true)
                    .build());
                steps.add(ImplementationPlan.ImplementationStep.builder()
                    .stepNumber(3)
                    .description("Rebuild fragmented indexes")
                    .command("ALTER INDEX REBUILD")
                    .type(ImplementationPlan.ImplementationStep.StepType.DDL)
                    .estimatedDurationMs(15 * 60 * 1000)
                    .canRollback(false)
                    .build());
                break;
                
            case "Query Rewrite":
                steps.add(ImplementationPlan.ImplementationStep.builder()
                    .stepNumber(1)
                    .description("Test rewritten query")
                    .command("Execute in test environment")
                    .type(ImplementationPlan.ImplementationStep.StepType.VALIDATION)
                    .estimatedDurationMs(10 * 60 * 1000)
                    .canRollback(true)
                    .build());
                steps.add(ImplementationPlan.ImplementationStep.builder()
                    .stepNumber(2)
                    .description("Update application code")
                    .command("Deploy new query")
                    .type(ImplementationPlan.ImplementationStep.StepType.CONFIG_CHANGE)
                    .estimatedDurationMs(20 * 60 * 1000)
                    .canRollback(true)
                    .build());
                break;
                
            case "Denormalization":
                steps.add(ImplementationPlan.ImplementationStep.builder()
                    .stepNumber(1)
                    .description("Design denormalized schema")
                    .command("Schema design document")
                    .type(ImplementationPlan.ImplementationStep.StepType.DDL)
                    .estimatedDurationMs(40 * 60 * 1000)
                    .canRollback(false)
                    .build());
                steps.add(ImplementationPlan.ImplementationStep.builder()
                    .stepNumber(2)
                    .description("Migrate data")
                    .command("ETL process")
                    .type(ImplementationPlan.ImplementationStep.StepType.DML)
                    .estimatedDurationMs(120 * 60 * 1000)
                    .canRollback(false)
                    .build());
                steps.add(ImplementationPlan.ImplementationStep.builder()
                    .stepNumber(3)
                    .description("Update application logic")
                    .command("Code changes")
                    .type(ImplementationPlan.ImplementationStep.StepType.CONFIG_CHANGE)
                    .estimatedDurationMs(80 * 60 * 1000)
                    .canRollback(true)
                    .build());
                break;
        }
        
        plan.setSteps(steps);
        plan.setEstimatedDurationMs(
            steps.stream().mapToLong(ImplementationPlan.ImplementationStep::getEstimatedDurationMs).sum()
        );
        
        return plan;
    }
    
    // Initialize optimization rules
    
    private List<OptimizationRule> initializeOptimizationRules() {
        List<OptimizationRule> rules = new ArrayList<>();
        
        // Rule: Missing WHERE clause
        rules.add(new OptimizationRule() {
            @Override
            public boolean applies(String query, QueryAnalysis analysis, QueryPlan plan) {
                return !query.toUpperCase().contains("WHERE") && 
                       query.toUpperCase().contains("FROM");
            }
            
            @Override
            public OptimizationRecommendation recommend(String query, QueryAnalysis analysis, QueryPlan plan) {
                return OptimizationRecommendation.builder()
                    .type(OptimizationRecommendation.OptimizationType.ADD_FILTER)
                    .description("Add WHERE clause to avoid full table scan")
                    .priority(OptimizationRecommendation.Priority.HIGH)
                    .expectedImprovement(60.0)
                    .build();
            }
        });
        
        // Rule: Cartesian product
        rules.add(new OptimizationRule() {
            @Override
            public boolean applies(String query, QueryAnalysis analysis, QueryPlan plan) {
                return query.toUpperCase().contains("FROM") && 
                       query.toUpperCase().contains(",") && 
                       !query.toUpperCase().contains("JOIN");
            }
            
            @Override
            public OptimizationRecommendation recommend(String query, QueryAnalysis analysis, QueryPlan plan) {
                return OptimizationRecommendation.builder()
                    .type(OptimizationRecommendation.OptimizationType.REWRITE_QUERY)
                    .description("Potential Cartesian product detected. Add proper JOIN conditions")
                    .priority(OptimizationRecommendation.Priority.CRITICAL)
                    .expectedImprovement(80.0)
                    .build();
            }
        });
        
        return rules;
    }
    
    // Supporting classes
    
    private interface OptimizationRule {
        boolean applies(String query, QueryAnalysis analysis, QueryPlan plan);
        OptimizationRecommendation recommend(String query, QueryAnalysis analysis, QueryPlan plan);
    }
    
    // Additional inner classes for optimization structures
    
    private static class PlanStep {
        private final String operation;
        private final String description;
        private final double cost;
        
        public PlanStep(String operation, String description, double cost) {
            this.operation = operation;
            this.description = description;
            this.cost = cost;
        }
        
        public double getCost() { return cost; }
    }
    
    private static class QueryPlan {
        private String query;
        private List<PlanStep> planSteps = new ArrayList<>();
        private double estimatedCost;
        
        public List<PlanStep> getPlanSteps() { return planSteps; }
        public void setPlanSteps(List<PlanStep> steps) { this.planSteps = steps; }
        public void setQuery(String query) { this.query = query; }
        public void setEstimatedCost(double cost) { this.estimatedCost = cost; }
    }
    
    private static class JoinOrderAnalysis {
        private boolean hasOptimalOrder;
        private double expectedImprovement;
        private String optimizedQuery;
        
        public boolean hasOptimalOrder() { return hasOptimalOrder; }
        public void setHasOptimalOrder(boolean has) { this.hasOptimalOrder = has; }
        public double getExpectedImprovement() { return expectedImprovement; }
        public void setExpectedImprovement(double improvement) { this.expectedImprovement = improvement; }
        public String getOptimizedQuery() { return optimizedQuery; }
        public void setOptimizedQuery(String query) { this.optimizedQuery = query; }
    }
}