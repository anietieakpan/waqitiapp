package com.waqiti.audit.repository;

import com.waqiti.audit.domain.DataLineage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for data lineage tracking and management
 */
@Repository
public interface DataLineageRepository extends JpaRepository<DataLineage, UUID> {
    
    /**
     * Find data lineage by entity
     */
    List<DataLineage> findByEntityTypeAndEntityId(String entityType, String entityId);
    
    /**
     * Find data lineage by source system
     */
    List<DataLineage> findBySourceSystem(String sourceSystem);
    
    /**
     * Find data lineage by target system
     */
    List<DataLineage> findByTargetSystem(String targetSystem);
    
    /**
     * Find data lineage within time range
     */
    @Query("SELECT d FROM DataLineage d WHERE d.timestamp BETWEEN :startTime AND :endTime")
    List<DataLineage> findByTimestampBetween(@Param("startTime") LocalDateTime startTime, 
                                              @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find upstream dependencies for an entity
     */
    @Query("SELECT d FROM DataLineage d WHERE d.targetEntityId = :entityId AND d.targetEntityType = :entityType")
    List<DataLineage> findUpstreamDependencies(@Param("entityType") String entityType, 
                                                @Param("entityId") String entityId);
    
    /**
     * Find downstream impacts for an entity
     */
    @Query("SELECT d FROM DataLineage d WHERE d.sourceEntityId = :entityId AND d.sourceEntityType = :entityType")
    List<DataLineage> findDownstreamImpacts(@Param("entityType") String entityType, 
                                           @Param("entityId") String entityId);
    
    /**
     * Find data lineage by transformation type
     */
    List<DataLineage> findByTransformationType(String transformationType);
    
    /**
     * Count lineage records by system
     */
    @Query("SELECT d.sourceSystem, COUNT(d) FROM DataLineage d GROUP BY d.sourceSystem")
    List<Object[]> countBySourceSystem();
    
    /**
     * Find critical data paths
     */
    @Query("SELECT d FROM DataLineage d WHERE d.isCriticalPath = true")
    List<DataLineage> findCriticalDataPaths();
    
    /**
     * Find data lineage with quality issues
     */
    @Query("SELECT d FROM DataLineage d WHERE d.dataQualityScore < :threshold")
    List<DataLineage> findDataQualityIssues(@Param("threshold") Double threshold);
}