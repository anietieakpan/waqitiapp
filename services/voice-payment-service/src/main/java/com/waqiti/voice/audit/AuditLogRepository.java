package com.waqiti.voice.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Audit Log Repository
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
