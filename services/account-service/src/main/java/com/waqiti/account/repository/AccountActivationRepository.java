package com.waqiti.account.repository;

import com.waqiti.account.domain.AccountActivation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountActivationRepository extends JpaRepository<AccountActivation, UUID> {
    
    List<AccountActivation> findByAccountId(UUID accountId);
    
    List<AccountActivation> findByUserId(UUID userId);
}
