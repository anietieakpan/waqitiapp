package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Account entity in wallet service
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    
    /**
     * Find account by account number
     */
    Optional<Account> findByAccountNumber(String accountNumber);
    
    /**
     * Find accounts by user ID
     */
    List<Account> findByUserId(UUID userId);
    
    /**
     * Find account by user ID and currency
     */
    Optional<Account> findByUserIdAndCurrency(UUID userId, String currency);
    
    /**
     * Check if account exists for user
     */
    boolean existsByUserIdAndCurrency(UUID userId, String currency);
    
    /**
     * Find active accounts by user ID
     */
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.status = 'ACTIVE'")
    List<Account> findActiveAccountsByUserId(@Param("userId") UUID userId);
}