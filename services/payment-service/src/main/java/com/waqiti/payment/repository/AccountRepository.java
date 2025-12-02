package com.waqiti.payment.repository;

import com.waqiti.payment.domain.Account;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Account entities
 */
@Repository
public interface AccountRepository extends MongoRepository<Account, String> {
    
    Optional<Account> findByUserId(String userId);
    
    Optional<Account> findByAccountNumber(String accountNumber);
    
    List<Account> findByUserIdAndIsActive(String userId, boolean isActive);
    
    List<Account> findByStatus(String status);
    
    @Query("{'userId': ?0, 'isFrozen': false, 'isRestricted': false, 'isActive': true}")
    List<Account> findActiveAccountsByUserId(String userId);
    
    @Query("{'userId': ?0, 'accountType': ?1}")
    Optional<Account> findByUserIdAndAccountType(String userId, String accountType);
    
    boolean existsByUserIdAndAccountNumber(String userId, String accountNumber);
    
    @Query("{'isFrozen': true}")
    List<Account> findAllFrozenAccounts();
    
    @Query("{'isRestricted': true}")
    List<Account> findAllRestrictedAccounts();
    
    long countByUserIdAndIsActive(String userId, boolean isActive);
}