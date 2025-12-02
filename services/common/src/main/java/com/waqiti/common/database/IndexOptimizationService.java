package com.waqiti.common.database;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for database index optimization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexOptimizationService {
    
    private final DataSource dataSource;
    
    public void suggestIndexes(List<String> tables) {
        log.info("Analyzing tables for index suggestions: {}", tables);
        
        try (Connection connection = dataSource.getConnection()) {
            for (String table : tables) {
                analyzeTableIndexes(connection, table);
            }
        } catch (SQLException e) {
            log.error("Failed to analyze indexes", e);
        }
    }
    
    public void cleanupUnusedIndexes() {
        log.info("Cleaning up unused indexes");
        
        try (Connection connection = dataSource.getConnection()) {
            List<UnusedIndex> unusedIndexes = findUnusedIndexes(connection);
            
            for (UnusedIndex index : unusedIndexes) {
                log.info("Found unused index: {} on table {}", index.getIndexName(), index.getTableName());
                // In production, you would carefully review before dropping
                // dropIndex(connection, index);
            }
        } catch (SQLException e) {
            log.error("Failed to cleanup unused indexes", e);
        }
    }
    
    public List<IndexRecommendation> analyzeQueryForIndexes(PredictedQuery query) {
        List<IndexRecommendation> recommendations = new ArrayList<>();
        
        try {
            String queryPattern = query.getQueryPattern();
            if (queryPattern == null) {
                return recommendations;
            }
            
            // Analyze query pattern for potential index opportunities
            if (queryPattern.toUpperCase().contains("WHERE")) {
                recommendations.addAll(analyzeWhereClause(queryPattern, query));
            }
            
            if (queryPattern.toUpperCase().contains("ORDER BY")) {
                recommendations.addAll(analyzeOrderByClause(queryPattern, query));
            }
            
            if (queryPattern.toUpperCase().contains("JOIN")) {
                recommendations.addAll(analyzeJoinClause(queryPattern, query));
            }
            
            log.debug("Generated {} index recommendations for query pattern: {}", 
                     recommendations.size(), queryPattern);
            
        } catch (Exception e) {
            log.error("Failed to analyze query for index recommendations", e);
        }
        
        return recommendations;
    }
    
    private List<IndexRecommendation> analyzeWhereClause(String queryPattern, PredictedQuery query) {
        List<IndexRecommendation> recommendations = new ArrayList<>();
        
        // Simple pattern matching for WHERE clauses
        // In a real implementation, you would use a proper SQL parser
        if (queryPattern.contains("account_id =")) {
            recommendations.add(IndexRecommendation.createBTreeRecommendation(
                "accounts", List.of("account_id"), null, 30.0));
        }
        
        if (queryPattern.contains("user_id =")) {
            recommendations.add(IndexRecommendation.createBTreeRecommendation(
                "transactions", List.of("user_id"), null, 25.0));
        }
        
        if (queryPattern.contains("created_at >")) {
            recommendations.add(IndexRecommendation.createBTreeRecommendation(
                "transactions", List.of("created_at"), null, 20.0));
        }
        
        return recommendations;
    }
    
    private List<IndexRecommendation> analyzeOrderByClause(String queryPattern, PredictedQuery query) {
        List<IndexRecommendation> recommendations = new ArrayList<>();
        
        if (queryPattern.contains("ORDER BY created_at")) {
            recommendations.add(IndexRecommendation.createBTreeRecommendation(
                "transactions", List.of("created_at"), null, 15.0));
        }
        
        return recommendations;
    }
    
    private List<IndexRecommendation> analyzeJoinClause(String queryPattern, PredictedQuery query) {
        List<IndexRecommendation> recommendations = new ArrayList<>();
        
        if (queryPattern.contains("JOIN") && queryPattern.contains("user_id")) {
            recommendations.add(IndexRecommendation.createBTreeRecommendation(
                "user_accounts", List.of("user_id"), null, 40.0));
        }
        
        return recommendations;
    }
    
    private void analyzeTableIndexes(Connection connection, String table) throws SQLException {
        // Analyze table structure and suggest indexes
        String query = "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, table);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    
                    // Basic index suggestion logic
                    if (columnName.endsWith("_id") || columnName.endsWith("_date")) {
                        log.debug("Consider indexing {}.{} ({})", table, columnName, dataType);
                    }
                }
            }
        }
    }
    
    private List<UnusedIndex> findUnusedIndexes(Connection connection) throws SQLException {
        List<UnusedIndex> unusedIndexes = new ArrayList<>();
        
        // This is a simplified example - actual implementation would vary by database
        String query = "SELECT schemaname, tablename, indexname, idx_scan " +
                      "FROM pg_stat_user_indexes " +
                      "WHERE idx_scan = 0 AND indexrelname NOT LIKE '%_pkey'";
        
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                UnusedIndex index = new UnusedIndex();
                index.setSchemaName(rs.getString("schemaname"));
                index.setTableName(rs.getString("tablename"));
                index.setIndexName(rs.getString("indexname"));
                index.setScanCount(rs.getInt("idx_scan"));
                unusedIndexes.add(index);
            }
        }
        
        return unusedIndexes;
    }
    
    public void createIndexIfNotExists(IndexRecommendation recommendation) {
        log.info("Creating index if not exists: {}", recommendation.getIndexName());
        // Implementation would check if index exists and create if needed
        
        try (Connection connection = dataSource.getConnection()) {
            String checkIndexQuery = "SELECT 1 FROM pg_indexes WHERE indexname = ?";
            try (PreparedStatement stmt = connection.prepareStatement(checkIndexQuery)) {
                stmt.setString(1, recommendation.getIndexName());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        // Index doesn't exist, create it
                        String createIndexSql = recommendation.getIndexDefinition();
                        try (PreparedStatement createStmt = connection.prepareStatement(createIndexSql)) {
                            createStmt.execute();
                            log.info("Successfully created index: {}", recommendation.getIndexName());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to create index: {}", recommendation.getIndexName(), e);
        }
    }
    
    public List<OptimizationRecommendation> getIndexRecommendations(QueryAnalysis analysis) {
        List<IndexRecommendation> indexRecommendations = new ArrayList<>();
        
        // Basic recommendations based on query analysis
        if (analysis.getQueryPattern().contains("WHERE")) {
            indexRecommendations.add(IndexRecommendation.createBTreeRecommendation(
                "transactions", List.of("user_id"), null, 25.0));
        }
        
        if (analysis.getQueryPattern().contains("ORDER BY")) {
            indexRecommendations.add(IndexRecommendation.createBTreeRecommendation(
                "transactions", List.of("created_at"), null, 15.0));
        }
        
        // Convert to OptimizationRecommendation
        return indexRecommendations.stream()
            .map(this::convertToOptimizationRecommendation)
            .collect(java.util.stream.Collectors.toList());
    }
    
    private OptimizationRecommendation convertToOptimizationRecommendation(IndexRecommendation indexRec) {
        return OptimizationRecommendation.builder()
            .type(OptimizationRecommendation.OptimizationType.ADD_INDEX)
            .description(indexRec.getJustification())
            .suggestedAction(indexRec.getIndexDefinition())
            .expectedImprovement(indexRec.getExpectedImprovementPercent())
            .priority(indexRec.isHighImpact() ? 
                     OptimizationRecommendation.Priority.HIGH : 
                     OptimizationRecommendation.Priority.MEDIUM)
            .implementationCode(indexRec.getIndexDefinition())
            .build();
    }
    
    @Data
    private static class UnusedIndex {
        private String schemaName;
        private String tableName;
        private String indexName;
        private int scanCount;
        private Instant lastUsed;
    }
}