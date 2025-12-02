package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.domain.SecurityAwarenessAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for SecurityAwarenessAuditLog entities
 *
 * @author Waqiti Platform Team
 */
@Repository
public interface SecurityAwarenessAuditRepository extends JpaRepository<SecurityAwarenessAuditLog, UUID> {

    /**
     * Find audit logs by employee
     */
    List<SecurityAwarenessAuditLog> findByEmployeeIdOrderByTimestampDesc(UUID employeeId);

    /**
     * Find audit logs by event type
     */
    List<SecurityAwarenessAuditLog> findByEventTypeOrderByTimestampDesc(
            SecurityAwarenessAuditLog.EventType eventType
    );
}