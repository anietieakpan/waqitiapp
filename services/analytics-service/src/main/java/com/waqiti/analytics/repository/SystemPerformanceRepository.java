package com.waqiti.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * SystemPerformanceRepository
 *
 * <p>Repository for System performance metrics
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 */
@Repository
public interface SystemPerformanceRepository extends JpaRepository<Object, UUID> {
    
    // Custom query methods will be added as needed
    
}
