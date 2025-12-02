package com.waqiti.infrastructure.repository;

import com.waqiti.infrastructure.domain.CapacityRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CapacityRepository extends MongoRepository<CapacityRecord, String> {
    
    List<CapacityRecord> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    List<CapacityRecord> findByResourceAndTimestampAfter(String resource, LocalDateTime timestamp);
    
    @Query("{'resource': ?0, 'timestamp': {'$gte': ?1, '$lte': ?2}}")
    List<CapacityRecord> findResourceUtilizationBetween(
        String resource, LocalDateTime start, LocalDateTime end);
    
    @Query("{'utilization': {'$gt': ?0}, 'timestamp': {'$gte': ?1}}")
    List<CapacityRecord> findHighUtilizationSince(Double threshold, LocalDateTime since);
    
    @Query("{'region': ?0, 'timestamp': {'$gte': ?1}}")
    List<CapacityRecord> findByRegionSince(String region, LocalDateTime since);
    
    List<CapacityRecord> findTop10ByResourceOrderByUtilizationDesc(String resource);
}