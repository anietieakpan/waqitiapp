package com.waqiti.infrastructure.repository;

import com.waqiti.infrastructure.domain.MonitoringRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MonitoringRepository extends MongoRepository<MonitoringRecord, String> {
    
    List<MonitoringRecord> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    List<MonitoringRecord> findByMetricNameAndTimestampAfter(String metricName, LocalDateTime timestamp);
    
    @Query("{'metricName': ?0, 'timestamp': {'$gte': ?1, '$lte': ?2}}")
    List<MonitoringRecord> findMetricDataBetween(String metricName, LocalDateTime start, LocalDateTime end);
    
    @Query("{'tags.service': ?0, 'timestamp': {'$gte': ?1}}")
    List<MonitoringRecord> findByServiceSince(String service, LocalDateTime since);
    
    @Query("{'value': {'$gt': ?0}, 'timestamp': {'$gte': ?1}}")
    List<MonitoringRecord> findAnomaliesSince(Double threshold, LocalDateTime since);
}