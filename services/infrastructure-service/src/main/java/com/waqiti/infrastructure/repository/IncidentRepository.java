package com.waqiti.infrastructure.repository;

import com.waqiti.infrastructure.domain.Incident;
import com.waqiti.infrastructure.domain.IncidentStatus;
import com.waqiti.infrastructure.domain.IncidentSeverity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IncidentRepository extends MongoRepository<Incident, String> {
    
    List<Incident> findByStatus(IncidentStatus status);
    
    List<Incident> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    List<Incident> findBySeverityAndStatus(IncidentSeverity severity, IncidentStatus status);
    
    @Query("{'status': {'$in': ['OPEN', 'IN_PROGRESS']}}")
    List<Incident> findActiveIncidents();
    
    @Query("{'severity': 'CRITICAL', 'createdAt': {'$gte': ?0}}")
    List<Incident> findCriticalIncidentsSince(LocalDateTime since);
    
    long countByStatusAndCreatedAtBetween(
        IncidentStatus status, LocalDateTime start, LocalDateTime end);
    
    @Query("{'affectedServices': {'$in': [?0]}}")
    List<Incident> findByAffectedService(String service);
}