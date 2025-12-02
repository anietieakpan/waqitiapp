package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.TransactionPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository for TransactionPattern entities
 */
@Repository
public interface TransactionPatternRepository extends JpaRepository<TransactionPattern, String> {
    
    List<TransactionPattern> findByUserId(String userId);
    
    List<TransactionPattern> findByPatternType(String patternType);
    
    List<TransactionPattern> findByUserIdAndPatternType(String userId, String patternType);
}
