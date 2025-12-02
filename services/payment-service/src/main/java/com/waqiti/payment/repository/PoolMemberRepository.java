package com.waqiti.payment.repository;

import com.waqiti.payment.entity.PoolMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PoolMemberRepository extends JpaRepository<PoolMember, Long> {
    
    boolean existsByPoolIdAndUserId(Long poolId, String userId);
    
    Optional<PoolMember> findByPoolIdAndUserId(Long poolId, String userId);
    
    List<PoolMember> findByPoolId(Long poolId);
    
    List<PoolMember> findByUserId(String userId);
    
    long countByPoolId(Long poolId);
    
    List<PoolMember> findByPoolIdAndStatus(Long poolId, Object status);
}