package com.waqiti.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * AnomalyAlertRepository
 *
 * <p>Repository for Anomaly alert data
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 */
@Repository
public interface AnomalyAlertRepository extends JpaRepository<Object, UUID> {
    
    // Custom query methods will be added as needed
    
}
