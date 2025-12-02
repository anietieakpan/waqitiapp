package com.waqiti.account.repository;

import com.waqiti.account.model.FinalStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Final Statement Repository - Production Implementation
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Repository
public interface FinalStatementRepository extends JpaRepository<FinalStatement, UUID> {

    /**
     * Find statement by account ID
     */
    Optional<FinalStatement> findByAccountId(String accountId);

    /**
     * Find statement by closure ID
     */
    Optional<FinalStatement> findByClosureId(UUID closureId);
}
