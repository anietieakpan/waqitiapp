package com.waqiti.ml.repository;

import com.waqiti.ml.domain.MLModel;
import com.waqiti.ml.domain.ModelStatus;
import com.waqiti.ml.domain.ModelType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelRepository extends JpaRepository<MLModel, UUID> {
    
    Optional<MLModel> findByModelNameAndVersion(String modelName, String version);
    
    List<MLModel> findByModelNameOrderByVersionDesc(String modelName);
    
    @Query("SELECT m FROM MLModel m WHERE m.modelName = :modelName " +
           "AND m.status = 'ACTIVE' ORDER BY m.version DESC")
    Optional<MLModel> findActiveModelByName(@Param("modelName") String modelName);
    
    Page<MLModel> findByStatus(ModelStatus status, Pageable pageable);
    
    Page<MLModel> findByModelType(ModelType modelType, Pageable pageable);
    
    Page<MLModel> findByStatusAndModelType(ModelStatus status, ModelType modelType, Pageable pageable);
    
    @Query("SELECT m FROM MLModel m WHERE m.status = 'ACTIVE' " +
           "AND m.modelType = :modelType ORDER BY m.accuracy DESC")
    List<MLModel> findBestActiveModelsByType(@Param("modelType") ModelType modelType);
    
    @Query("SELECT m FROM MLModel m WHERE m.lastTrainedDate < :date " +
           "AND m.status = 'ACTIVE'")
    List<MLModel> findModelsNeedingRetraining(@Param("date") LocalDateTime date);
    
    @Query("SELECT COUNT(m) FROM MLModel m WHERE m.status = :status")
    Long countByStatus(@Param("status") ModelStatus status);
    
    @Query("SELECT m.modelType, COUNT(m) FROM MLModel m WHERE m.status = 'ACTIVE' " +
           "GROUP BY m.modelType")
    List<Object[]> countActiveModelsByType();
    
    @Query("SELECT m FROM MLModel m WHERE m.status = 'ACTIVE' " +
           "ORDER BY m.accuracy DESC")
    Page<MLModel> findTopPerformingModels(Pageable pageable);
    
    @Query("SELECT m FROM MLModel m WHERE m.status = 'ACTIVE' " +
           "AND m.inferenceCount > 0 " +
           "ORDER BY m.inferenceCount DESC")
    Page<MLModel> findMostUsedModels(Pageable pageable);
    
    @Modifying
    @Query("UPDATE MLModel m SET m.status = :status, m.lastModifiedDate = :date " +
           "WHERE m.id = :modelId")
    void updateModelStatus(@Param("modelId") UUID modelId,
                          @Param("status") ModelStatus status,
                          @Param("date") LocalDateTime date);
    
    @Modifying
    @Query("UPDATE MLModel m SET m.inferenceCount = m.inferenceCount + 1, " +
           "m.lastInferenceDate = :date WHERE m.id = :modelId")
    void incrementInferenceCount(@Param("modelId") UUID modelId,
                                @Param("date") LocalDateTime date);
    
    @Modifying
    @Query("UPDATE MLModel m SET m.accuracy = :accuracy, " +
           "m.lastEvaluatedDate = :date WHERE m.id = :modelId")
    void updateModelAccuracy(@Param("modelId") UUID modelId,
                            @Param("accuracy") Double accuracy,
                            @Param("date") LocalDateTime date);
    
    @Query("SELECT m FROM MLModel m WHERE m.deploymentId = :deploymentId")
    Optional<MLModel> findByDeploymentId(@Param("deploymentId") String deploymentId);
    
    @Query("SELECT m FROM MLModel m WHERE m.status = 'ACTIVE' " +
           "AND m.modelType IN :types")
    List<MLModel> findActiveModelsByTypes(@Param("types") List<ModelType> types);
    
    @Query("SELECT DISTINCT m.modelName FROM MLModel m ORDER BY m.modelName")
    List<String> findAllModelNames();
    
    @Query("SELECT m FROM MLModel m WHERE m.createdDate BETWEEN :startDate AND :endDate " +
           "ORDER BY m.createdDate DESC")
    List<MLModel> findModelsCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    // SECURITY FIX: Properly escape wildcards to prevent wildcard injection attacks
    @Query("SELECT m FROM MLModel m WHERE m.tags LIKE CONCAT('%', REPLACE(REPLACE(REPLACE(:tag, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_'), '%')")
    List<MLModel> findModelsByTag(@Param("tag") String tag);
    
    boolean existsByModelNameAndVersion(String modelName, String version);
    
    void deleteByStatusAndLastModifiedDateBefore(ModelStatus status, LocalDateTime date);
}