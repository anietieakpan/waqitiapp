package com.waqiti.rewards.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for Rewards Service
 * Manages authentication and authorization for loyalty and rewards operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class RewardsKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain rewardsKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Rewards Service");
        
        return createKeycloakSecurityFilterChain(http, "rewards-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**").permitAll()
                .requestMatchers("/api/v1/rewards/public/**").permitAll()
                .requestMatchers("/api/v1/rewards/programs/public").permitAll() // Public rewards programs
                
                // Points Management
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/points/balance").hasAuthority("SCOPE_rewards:points-balance")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/points/history").hasAuthority("SCOPE_rewards:points-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/points/earn").hasAuthority("SCOPE_rewards:points-earn")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/points/redeem").hasAuthority("SCOPE_rewards:points-redeem")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/points/transfer").hasAuthority("SCOPE_rewards:points-transfer")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/points/expiring").hasAuthority("SCOPE_rewards:points-expiring")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/points/convert").hasAuthority("SCOPE_rewards:points-convert")
                
                // Cashback Management
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/cashback/balance").hasAuthority("SCOPE_rewards:cashback-balance")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/cashback/history").hasAuthority("SCOPE_rewards:cashback-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/cashback/claim").hasAuthority("SCOPE_rewards:cashback-claim")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/cashback/pending").hasAuthority("SCOPE_rewards:cashback-pending")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/cashback/withdraw").hasAuthority("SCOPE_rewards:cashback-withdraw")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/cashback/rates").hasAuthority("SCOPE_rewards:cashback-rates")
                
                // Tier Management
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/tier/current").hasAuthority("SCOPE_rewards:tier-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/tier/progress").hasAuthority("SCOPE_rewards:tier-progress")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/tier/benefits").hasAuthority("SCOPE_rewards:tier-benefits")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/tier/history").hasAuthority("SCOPE_rewards:tier-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/tier/upgrade").hasAuthority("SCOPE_rewards:tier-upgrade")
                
                // Campaigns & Promotions
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/campaigns").hasAuthority("SCOPE_rewards:campaigns-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/campaigns/*").hasAuthority("SCOPE_rewards:campaign-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/campaigns/*/enroll").hasAuthority("SCOPE_rewards:campaign-enroll")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/campaigns/*/unenroll").hasAuthority("SCOPE_rewards:campaign-unenroll")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/campaigns/*/progress").hasAuthority("SCOPE_rewards:campaign-progress")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/campaigns/*/claim").hasAuthority("SCOPE_rewards:campaign-claim")
                
                // Challenges & Achievements
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/challenges").hasAuthority("SCOPE_rewards:challenges-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/challenges/*/progress").hasAuthority("SCOPE_rewards:challenge-progress")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/challenges/*/join").hasAuthority("SCOPE_rewards:challenge-join")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/challenges/*/complete").hasAuthority("SCOPE_rewards:challenge-complete")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/achievements").hasAuthority("SCOPE_rewards:achievements-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/achievements/*/claim").hasAuthority("SCOPE_rewards:achievement-claim")
                
                // Referral Program
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/referral/code").hasAuthority("SCOPE_rewards:referral-code")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/referral/generate").hasAuthority("SCOPE_rewards:referral-generate")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/referral/stats").hasAuthority("SCOPE_rewards:referral-stats")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/referral/earnings").hasAuthority("SCOPE_rewards:referral-earnings")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/referral/invite").hasAuthority("SCOPE_rewards:referral-invite")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/referral/claim").hasAuthority("SCOPE_rewards:referral-claim")
                
                // Rewards Catalog
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/catalog").hasAuthority("SCOPE_rewards:catalog-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/catalog/categories").hasAuthority("SCOPE_rewards:catalog-categories")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/catalog/*/details").hasAuthority("SCOPE_rewards:catalog-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/catalog/*/redeem").hasAuthority("SCOPE_rewards:catalog-redeem")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/catalog/featured").hasAuthority("SCOPE_rewards:catalog-featured")
                
                // Vouchers & Coupons
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/vouchers").hasAuthority("SCOPE_rewards:vouchers-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/vouchers/*/details").hasAuthority("SCOPE_rewards:voucher-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/vouchers/*/use").hasAuthority("SCOPE_rewards:voucher-use")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/vouchers/active").hasAuthority("SCOPE_rewards:vouchers-active")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/vouchers/expired").hasAuthority("SCOPE_rewards:vouchers-expired")
                
                // Partner Rewards
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/partners").hasAuthority("SCOPE_rewards:partners-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/partners/*/offers").hasAuthority("SCOPE_rewards:partner-offers")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/partners/*/link").hasAuthority("SCOPE_rewards:partner-link")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/partners/*/sync").hasAuthority("SCOPE_rewards:partner-sync")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/partners/*/points").hasAuthority("SCOPE_rewards:partner-points")
                
                // Leaderboards & Competitions
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/leaderboard").hasAuthority("SCOPE_rewards:leaderboard-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/leaderboard/friends").hasAuthority("SCOPE_rewards:leaderboard-friends")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/leaderboard/*/position").hasAuthority("SCOPE_rewards:leaderboard-position")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/competitions").hasAuthority("SCOPE_rewards:competitions-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/competitions/*/join").hasAuthority("SCOPE_rewards:competition-join")
                
                // Analytics & Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/analytics/summary").hasAuthority("SCOPE_rewards:analytics-summary")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/analytics/trends").hasAuthority("SCOPE_rewards:analytics-trends")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/reports/monthly").hasAuthority("SCOPE_rewards:reports-monthly")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/reports/yearly").hasAuthority("SCOPE_rewards:reports-yearly")
                
                // Notifications & Preferences
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/preferences").hasAuthority("SCOPE_rewards:preferences-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/rewards/preferences").hasAuthority("SCOPE_rewards:preferences-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/rewards/notifications").hasAuthority("SCOPE_rewards:notifications-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/rewards/notifications/settings").hasAuthority("SCOPE_rewards:notifications-update")
                
                // Admin Operations - Campaign Management
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/admin/campaigns/create").hasRole("REWARDS_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/rewards/admin/campaigns/*").hasRole("REWARDS_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/rewards/admin/campaigns/*").hasRole("REWARDS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/admin/campaigns/*/activate").hasRole("REWARDS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/admin/campaigns/*/deactivate").hasRole("REWARDS_ADMIN")
                
                // Admin Operations - Points Management
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/admin/points/credit").hasRole("REWARDS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/admin/points/debit").hasRole("REWARDS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/admin/points/adjust").hasRole("REWARDS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/admin/points/expire").hasRole("REWARDS_ADMIN")
                
                // Admin Operations - Tier Management
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/admin/tiers/create").hasRole("REWARDS_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/rewards/admin/tiers/*").hasRole("REWARDS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/rewards/admin/tiers/*/override").hasRole("REWARDS_ADMIN")
                
                // Admin Operations - Analytics
                .requestMatchers("/api/v1/rewards/admin/**").hasRole("REWARDS_ADMIN")
                .requestMatchers("/api/v1/rewards/admin/analytics/**").hasRole("REWARDS_ANALYST")
                .requestMatchers("/api/v1/rewards/admin/reports/**").hasRole("REWARDS_ANALYST")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/rewards/**").hasRole("SERVICE")
                .requestMatchers("/internal/points/**").hasRole("SERVICE")
                .requestMatchers("/internal/cashback/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}