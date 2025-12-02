package com.waqiti.payment.dwolla.repository;

import com.waqiti.payment.dwolla.model.DwollaCustomerRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Dwolla customer records
 */
@Repository
public interface DwollaCustomerRepository extends MongoRepository<DwollaCustomerRecord, String> {
    
    Optional<DwollaCustomerRecord> findByUserId(String userId);
    
    Optional<DwollaCustomerRecord> findByDwollaCustomerId(String dwollaCustomerId);
    
    Optional<DwollaCustomerRecord> findByEmail(String email);
    
    List<DwollaCustomerRecord> findByStatus(String status);
    
    List<DwollaCustomerRecord> findByKycVerified(boolean kycVerified);
    
    @Query("{'createdAt': {$gte: ?0, $lte: ?1}}")
    List<DwollaCustomerRecord> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("{'status': ?0, 'retryCount': {$lt: ?1}}")
    List<DwollaCustomerRecord> findByStatusAndRetryCountLessThan(String status, int maxRetries);
    
    @Query("{'kycStatus': 'pending', 'createdAt': {$lt: ?0}}")
    List<DwollaCustomerRecord> findPendingKycOlderThan(LocalDateTime threshold);
    
    boolean existsByUserId(String userId);
    
    boolean existsByEmail(String email);
    
    boolean existsByDwollaCustomerId(String dwollaCustomerId);
    
    long countByStatus(String status);
    
    long countByKycVerified(boolean kycVerified);
}