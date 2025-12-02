package com.waqiti.common.repository;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Collection;

/**
 * Base repository for efficient batch operations
 */
@Repository
public abstract class BatchOperationRepository<T> {
    
    private final JdbcTemplate jdbcTemplate;
    private static final int BATCH_SIZE = 1000;
    
    public BatchOperationRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setFetchSize(1000);
    }
    
    /**
     * Performs batch insert with automatic chunking
     */
    @Transactional
    public void batchInsert(String sql, List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        
        // Process in chunks to avoid memory issues
        for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
            List<T> batch = entities.subList(i, 
                Math.min(i + BATCH_SIZE, entities.size()));
            
            jdbcTemplate.batchUpdate(sql, batch, batch.size(),
                (PreparedStatement ps, T entity) -> setInsertParameters(ps, entity));
        }
    }
    
    /**
     * Performs batch update with automatic chunking
     */
    @Transactional
    public void batchUpdate(String sql, List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        
        // Process in chunks
        for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
            List<T> batch = entities.subList(i, 
                Math.min(i + BATCH_SIZE, entities.size()));
            
            jdbcTemplate.batchUpdate(sql, batch, batch.size(),
                (PreparedStatement ps, T entity) -> setUpdateParameters(ps, entity));
        }
    }
    
    /**
     * Performs batch delete with automatic chunking
     */
    @Transactional
    public void batchDelete(String sql, List<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        
        // Process in chunks
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            List<?> batch = ids.subList(i, 
                Math.min(i + BATCH_SIZE, ids.size()));
            
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setObject(1, batch.get(i));
                }
                
                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        }
    }
    
    /**
     * Performs batch upsert (INSERT ... ON CONFLICT UPDATE)
     */
    @Transactional
    public void batchUpsert(String sql, List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        
        // Process in chunks
        for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
            List<T> batch = entities.subList(i, 
                Math.min(i + BATCH_SIZE, entities.size()));
            
            jdbcTemplate.batchUpdate(sql, batch, batch.size(),
                (PreparedStatement ps, T entity) -> setUpsertParameters(ps, entity));
        }
    }
    
    /**
     * Execute batch query with result streaming
     */
    public void streamQuery(String sql, Object[] params, ResultProcessor<T> processor) {
        jdbcTemplate.setFetchSize(1000);
        jdbcTemplate.query(sql, params, rs -> {
            while (rs.next()) {
                T entity = mapRow(rs);
                processor.process(entity);
            }
        });
    }
    
    /**
     * Abstract methods to be implemented by subclasses
     */
    protected abstract void setInsertParameters(PreparedStatement ps, T entity) throws SQLException;
    protected abstract void setUpdateParameters(PreparedStatement ps, T entity) throws SQLException;
    protected abstract void setUpsertParameters(PreparedStatement ps, T entity) throws SQLException;
    protected abstract T mapRow(java.sql.ResultSet rs) throws SQLException;
    
    /**
     * Functional interface for processing streamed results
     */
    @FunctionalInterface
    public interface ResultProcessor<T> {
        void process(T entity);
    }
}