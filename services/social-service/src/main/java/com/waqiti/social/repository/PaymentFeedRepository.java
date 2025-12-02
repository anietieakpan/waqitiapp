package com.waqiti.social.repository;

import com.waqiti.social.model.PaymentFeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentFeedRepository extends JpaRepository<PaymentFeed, UUID> {
}