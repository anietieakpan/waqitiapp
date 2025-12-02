package com.waqiti.infrastructure.repository;

import com.waqiti.infrastructure.domain.SystemHealthRecord;
import com.waqiti.infrastructure.domain.SystemStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SystemHealthRepository extends MongoRepository<SystemHealthRecord, String> {
    
    List<SystemHealthRecord> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    List<SystemHealthRecord> findByOverallStatusAndTimestampAfter(
        SystemStatus status, LocalDateTime timestamp);
    
    @Query("{'timestamp': {'$gte': ?0, '$lte': ?1}, 'overallStatus': {'$ne': 'HEALTHY'}}")
    List<SystemHealthRecord> findUnhealthyRecordsBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("{'timestamp': {'$gte': ?0}}")
    List<SystemHealthRecord> findRecentHealthChecks(LocalDateTime since);
    
    long countByOverallStatusAndTimestampBetween(
        SystemStatus status, LocalDateTime start, LocalDateTime end);
}