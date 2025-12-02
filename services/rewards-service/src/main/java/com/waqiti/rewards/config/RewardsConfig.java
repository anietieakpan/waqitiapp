package com.waqiti.rewards.config;

import com.waqiti.common.config.CommonConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Configuration for the Rewards Service
 */
@Configuration
@Import(CommonConfig.class)
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class RewardsConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            SecurityContext context = SecurityContextHolder.getContext();
            if (context != null && context.getAuthentication() != null) {
                Authentication auth = context.getAuthentication();
                if (auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                    return Optional.of(auth.getName());
                }
            }
            return Optional.of("system");
        };
    }

    @Bean(name = "rewardsTaskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("rewards-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @ConfigurationProperties(prefix = "rewards")
    @Bean
    public RewardsProperties rewardsProperties() {
        return new RewardsProperties();
    }
}