package com.waqiti.account.repository;

import com.waqiti.account.model.AccountHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Account History Repository - Production Implementation
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Repository
public interface AccountHistoryRepository extends JpaRepository<AccountHistory, UUID> {

    /**
     * Find history by account ID
     */
    List<AccountHistory> findByAccountIdOrderByCreatedAtDesc(String accountId);

    /**
     * Find history by event type
     */
    List<AccountHistory> findByEventType(String eventType);

    /**
     * Find history by account and event type
     */
    List<AccountHistory> findByAccountIdAndEventType(String accountId, String eventType);
}
