package com.waqiti.common.security.awareness.health;

import com.waqiti.common.security.awareness.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("securityAwarenessHealth")
@RequiredArgsConstructor
public class SecurityAwarenessHealthIndicator implements HealthIndicator {

    private final SecurityTrainingModuleRepository moduleRepository;
    private final EmployeeSecurityProfileRepository profileRepository;

    @Override
    public Health health() {
        try {
            long activeModules = moduleRepository.count();
            long employeeProfiles = profileRepository.count();

            return Health.up()
                    .withDetail("active_training_modules", activeModules)
                    .withDetail("employee_profiles", employeeProfiles)
                    .withDetail("status", "operational")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}