package com.waqiti.payment.integration.paypal.repository;

import com.waqiti.payment.integration.paypal.domain.PayPalCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * PayPal customer repository
 */
@Repository
public interface PayPalCustomerRepository extends JpaRepository<PayPalCustomer, UUID> {
    
    Optional<PayPalCustomer> findByUserId(UUID userId);
    
    Optional<PayPalCustomer> findByPaypalCustomerId(String paypalCustomerId);
    
    @Query("SELECT pc FROM PayPalCustomer pc WHERE pc.userId = :userId AND pc.isActive = true")
    Optional<PayPalCustomer> findActiveByUserId(@Param("userId") UUID userId);
}