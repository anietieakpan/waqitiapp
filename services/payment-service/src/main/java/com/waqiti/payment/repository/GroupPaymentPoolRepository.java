package com.waqiti.payment.repository;

import com.waqiti.payment.entity.GroupPaymentPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupPaymentPoolRepository extends JpaRepository<GroupPaymentPool, Long> {
    
    Optional<GroupPaymentPool> findByPoolId(String poolId);
    
    List<GroupPaymentPool> findByCreatedBy(String userId);
    
    List<GroupPaymentPool> findByStatus(Object status);
    
    boolean existsByPoolId(String poolId);
}