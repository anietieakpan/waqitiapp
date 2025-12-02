package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.CardIncident;
import com.waqiti.virtualcard.domain.CardIncident.IncidentStatus;
import com.waqiti.virtualcard.domain.enums.ReplacementReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Card Incident operations
 */
@Repository
public interface CardIncidentRepository extends JpaRepository<CardIncident, String> {
    
    /**
     * Find incidents by card ID
     */
    List<CardIncident> findByCardIdOrderByReportedAtDesc(String cardId);
    
    /**
     * Find incidents by user ID
     */
    List<CardIncident> findByUserIdOrderByReportedAtDesc(String userId);
    
    /**
     * Find incidents by type
     */
    List<CardIncident> findByType(ReplacementReason type);
    
    /**
     * Find incidents by status
     */
    List<CardIncident> findByStatus(IncidentStatus status);
    
    /**
     * Find open incidents (not resolved or closed)
     */
    @Query("SELECT i FROM CardIncident i WHERE i.status IN ('REPORTED', 'UNDER_INVESTIGATION', 'ESCALATED')")
    List<CardIncident> findOpenIncidents();
    
    /**
     * Find incidents requiring investigation
     */
    List<CardIncident> findByStatusIn(List<IncidentStatus> statuses);
    
    /**
     * Find incidents reported within date range
     */
    @Query("SELECT i FROM CardIncident i WHERE i.reportedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY i.reportedAt DESC")
    List<CardIncident> findByReportedAtBetween(@Param("startDate") Instant startDate,
                                               @Param("endDate") Instant endDate);
    
    /**
     * Find incidents with police reports
     */
    @Query("SELECT i FROM CardIncident i WHERE i.policeReportNumber IS NOT NULL AND i.policeReportNumber != ''")
    List<CardIncident> findIncidentsWithPoliceReports();
    
    /**
     * Find incidents by investigation ID
     */
    Optional<CardIncident> findByInvestigationId(String investigationId);
    
    /**
     * Count incidents by type
     */
    @Query("SELECT i.type, COUNT(i) FROM CardIncident i GROUP BY i.type")
    List<Object[]> countIncidentsByType();
    
    /**
     * Count incidents by status
     */
    @Query("SELECT i.status, COUNT(i) FROM CardIncident i GROUP BY i.status")
    List<Object[]> countIncidentsByStatus();
}