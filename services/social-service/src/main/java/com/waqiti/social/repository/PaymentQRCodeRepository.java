package com.waqiti.social.repository;

import com.waqiti.social.model.PaymentQRCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentQRCodeRepository extends JpaRepository<PaymentQRCode, UUID> {
    
    Optional<PaymentQRCode> findById(String id);
}