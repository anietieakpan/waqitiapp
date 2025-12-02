package com.waqiti.infrastructure.repository;

import com.waqiti.infrastructure.domain.BackupRecord;
import com.waqiti.infrastructure.domain.BackupStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BackupRepository extends MongoRepository<BackupRecord, String> {
    
    List<BackupRecord> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    List<BackupRecord> findByStatusAndTimestampAfter(BackupStatus status, LocalDateTime timestamp);
    
    @Query("{'timestamp': {'$gte': ?0, '$lte': ?1}}")
    List<BackupRecord> findBackupsBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("{'status': {'$in': ['PARTIAL_FAILURE', 'FAILED']}, 'timestamp': {'$gte': ?0}}")
    List<BackupRecord> findFailedBackupsSince(LocalDateTime since);
    
    long countByStatusAndTimestampBetween(
        BackupStatus status, LocalDateTime start, LocalDateTime end);
    
    @Query("{'timestamp': {'$gte': ?0}}")
    List<BackupRecord> findRecentBackups(LocalDateTime since);
}