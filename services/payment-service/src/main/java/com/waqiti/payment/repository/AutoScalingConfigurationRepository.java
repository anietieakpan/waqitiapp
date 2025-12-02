package com.waqiti.payment.repository;

import com.waqiti.payment.domain.AutoScalingConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Auto Scaling Configuration
 */
@Repository
public interface AutoScalingConfigurationRepository extends MongoRepository<AutoScalingConfiguration, String> {

    Optional<AutoScalingConfiguration> findByServiceName(String serviceName);

    Optional<AutoScalingConfiguration> findByServiceNameAndEnabledTrue(String serviceName);
}
