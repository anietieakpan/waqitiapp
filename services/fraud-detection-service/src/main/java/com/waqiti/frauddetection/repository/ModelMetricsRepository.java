package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.ModelMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ModelMetrics entities
 */
@Repository
public interface ModelMetricsRepository extends JpaRepository<ModelMetrics, String> {
    
    List<ModelMetrics> findByModelName(String modelName);
    
    List<ModelMetrics> findByModelNameAndModelVersion(String modelName, String modelVersion);
    
    @Query("SELECT m FROM ModelMetrics m WHERE m.modelName = :modelName ORDER BY m.recordedAt DESC")
    Optional<ModelMetrics> findLatestByModelName(String modelName);
    
    @Query("SELECT m FROM ModelMetrics m ORDER BY m.accuracy DESC")
    List<ModelMetrics> findTopPerformingModels();
}
