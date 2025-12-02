package com.waqiti.social.repository;

import com.waqiti.social.model.PaymentChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentChallengeRepository extends JpaRepository<PaymentChallenge, UUID> {
}