package com.waqiti.user.repository;

import com.waqiti.user.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByUserId(String userId);

    List<PaymentMethod> findByUserIdAndStatus(String userId, String status);

    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.status = :status WHERE pm.userId = :userId")
    int updateStatusByUserId(@Param("userId") String userId, @Param("status") String status);

    @Modifying
    @Query("DELETE FROM PaymentMethod pm WHERE pm.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(pm) FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.status = 'ACTIVE'")
    long countActiveByUserId(@Param("userId") String userId);
    
    /**
     * Find active payment methods by user ID
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.status = 'ACTIVE'")
    List<PaymentMethod> findActiveByUserId(@Param("userId") String userId);
}
