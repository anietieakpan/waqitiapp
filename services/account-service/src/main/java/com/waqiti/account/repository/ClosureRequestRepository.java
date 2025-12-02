package com.waqiti.account.repository;

import com.waqiti.account.model.ClosureRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Closure Request Repository - Production Implementation
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Repository
public interface ClosureRequestRepository extends JpaRepository<ClosureRequest, UUID> {

    /**
     * Find request by account ID
     */
    Optional<ClosureRequest> findByAccountId(String accountId);

    /**
     * Find requests by status
     */
    List<ClosureRequest> findByStatus(String status);

    /**
     * Check if account has pending request
     */
    boolean existsByAccountIdAndStatus(String accountId, String status);
}
