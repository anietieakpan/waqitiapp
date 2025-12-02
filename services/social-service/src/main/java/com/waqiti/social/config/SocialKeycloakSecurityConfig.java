package com.waqiti.social.config;

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
 * Keycloak security configuration for Social Service
 * Manages authentication and authorization for social networking features
 * Including connections, groups, challenges, and social payments
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class SocialKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain socialKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Social Service");
        
        return createKeycloakSecurityFilterChain(http, "social-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/social/public/**").permitAll()
                
                // Social Connections & Friends Management
                .requestMatchers(HttpMethod.POST, "/api/v1/social/connections/request").hasAuthority("SCOPE_social:connection-request")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/connections/*/accept").hasAuthority("SCOPE_social:connection-accept")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/connections/*/decline").hasAuthority("SCOPE_social:connection-decline")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/connections/*").hasAuthority("SCOPE_social:connection-remove")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/connections").hasAuthority("SCOPE_social:connections-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/connections/pending").hasAuthority("SCOPE_social:connections-pending")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/connections/suggestions").hasAuthority("SCOPE_social:connection-suggestions")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/connections/*/block").hasAuthority("SCOPE_social:connection-block")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/connections/*/unblock").hasAuthority("SCOPE_social:connection-unblock")
                
                // Groups Management
                .requestMatchers(HttpMethod.POST, "/api/v1/social/groups/create").hasAuthority("SCOPE_social:group-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/groups").hasAuthority("SCOPE_social:groups-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/groups/*").hasAuthority("SCOPE_social:group-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/groups/*").hasAuthority("SCOPE_social:group-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/groups/*").hasAuthority("SCOPE_social:group-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/groups/*/join").hasAuthority("SCOPE_social:group-join")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/groups/*/leave").hasAuthority("SCOPE_social:group-leave")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/groups/*/invite").hasAuthority("SCOPE_social:group-invite")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/groups/*/members").hasAuthority("SCOPE_social:group-members")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/groups/*/members/*/remove").hasAuthority("SCOPE_social:group-member-remove")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/groups/*/members/*/promote").hasAuthority("SCOPE_social:group-member-promote")
                
                // Group Activities & Communication
                .requestMatchers(HttpMethod.POST, "/api/v1/social/groups/*/posts").hasAuthority("SCOPE_social:group-post")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/groups/*/posts").hasAuthority("SCOPE_social:group-posts-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/groups/*/posts/*").hasAuthority("SCOPE_social:group-post-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/groups/*/posts/*").hasAuthority("SCOPE_social:group-post-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/groups/*/posts/*/comments").hasAuthority("SCOPE_social:group-comment")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/groups/*/posts/*/like").hasAuthority("SCOPE_social:group-like")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/groups/*/posts/*/like").hasAuthority("SCOPE_social:group-unlike")
                
                // Challenges & Competitions
                .requestMatchers(HttpMethod.POST, "/api/v1/social/challenges/create").hasAuthority("SCOPE_social:challenge-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/challenges").hasAuthority("SCOPE_social:challenges-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/challenges/*").hasAuthority("SCOPE_social:challenge-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/challenges/*/join").hasAuthority("SCOPE_social:challenge-join")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/challenges/*/leave").hasAuthority("SCOPE_social:challenge-leave")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/challenges/*/progress").hasAuthority("SCOPE_social:challenge-progress")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/challenges/*/leaderboard").hasAuthority("SCOPE_social:challenge-leaderboard")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/challenges/*/complete").hasAuthority("SCOPE_social:challenge-complete")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/challenges/active").hasAuthority("SCOPE_social:challenges-active")
                
                // Social Feed & Timeline
                .requestMatchers(HttpMethod.GET, "/api/v1/social/feed").hasAuthority("SCOPE_social:feed-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/feed/post").hasAuthority("SCOPE_social:feed-post")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/feed/posts/*").hasAuthority("SCOPE_social:feed-post-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/feed/posts/*").hasAuthority("SCOPE_social:feed-post-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/feed/posts/*/like").hasAuthority("SCOPE_social:feed-like")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/feed/posts/*/like").hasAuthority("SCOPE_social:feed-unlike")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/feed/posts/*/comment").hasAuthority("SCOPE_social:feed-comment")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/feed/posts/*/share").hasAuthority("SCOPE_social:feed-share")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/feed/trending").hasAuthority("SCOPE_social:feed-trending")
                
                // Social Payments & Money Transfers
                .requestMatchers(HttpMethod.POST, "/api/v1/social/payments/request").hasAuthority("SCOPE_social:payment-request")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/payments/send").hasAuthority("SCOPE_social:payment-send")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/payments/requests").hasAuthority("SCOPE_social:payment-requests-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/payments/requests/*/accept").hasAuthority("SCOPE_social:payment-request-accept")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/payments/requests/*/decline").hasAuthority("SCOPE_social:payment-request-decline")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/payments/split-bill").hasAuthority("SCOPE_social:payment-split-bill")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/payments/history").hasAuthority("SCOPE_social:payment-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/payments/group-collect").hasAuthority("SCOPE_social:payment-group-collect")
                
                // Profile & User Management
                .requestMatchers(HttpMethod.GET, "/api/v1/social/profile").hasAuthority("SCOPE_social:profile-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/profile").hasAuthority("SCOPE_social:profile-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/profile/*/public").hasAuthority("SCOPE_social:profile-public-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/profile/avatar").hasAuthority("SCOPE_social:profile-avatar-update")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/profile/privacy").hasAuthority("SCOPE_social:profile-privacy-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/profile/activity").hasAuthority("SCOPE_social:profile-activity")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/profile/settings").hasAuthority("SCOPE_social:profile-settings")
                
                // Privacy & Security Controls
                .requestMatchers(HttpMethod.GET, "/api/v1/social/privacy/settings").hasAuthority("SCOPE_social:privacy-settings")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/privacy/settings").hasAuthority("SCOPE_social:privacy-settings-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/privacy/blocked-users").hasAuthority("SCOPE_social:blocked-users-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/privacy/block/*").hasAuthority("SCOPE_social:user-block")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/privacy/unblock/*").hasAuthority("SCOPE_social:user-unblock")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/privacy/report").hasAuthority("SCOPE_social:user-report")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/privacy/reports").hasAuthority("SCOPE_social:reports-view")
                
                // Gamification & Rewards
                .requestMatchers(HttpMethod.GET, "/api/v1/social/gamification/badges").hasAuthority("SCOPE_social:badges-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/gamification/achievements").hasAuthority("SCOPE_social:achievements-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/gamification/leaderboards").hasAuthority("SCOPE_social:leaderboards-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/gamification/points").hasAuthority("SCOPE_social:points-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/gamification/claim-reward").hasAuthority("SCOPE_social:reward-claim")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/gamification/streaks").hasAuthority("SCOPE_social:streaks-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/gamification/progress").hasAuthority("SCOPE_social:progress-view")
                
                // Events & Activities
                .requestMatchers(HttpMethod.POST, "/api/v1/social/events/create").hasAuthority("SCOPE_social:event-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/events").hasAuthority("SCOPE_social:events-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/events/*").hasAuthority("SCOPE_social:event-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/events/*").hasAuthority("SCOPE_social:event-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/events/*/attend").hasAuthority("SCOPE_social:event-attend")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/events/*/cancel-attendance").hasAuthority("SCOPE_social:event-cancel-attendance")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/events/*/invite").hasAuthority("SCOPE_social:event-invite")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/events/*/attendees").hasAuthority("SCOPE_social:event-attendees")
                
                // Content Moderation & Safety
                .requestMatchers(HttpMethod.POST, "/api/v1/social/moderation/report-content").hasAuthority("SCOPE_social:content-report")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/moderation/reports").hasAuthority("SCOPE_social:moderation-reports")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/moderation/appeals").hasAuthority("SCOPE_social:moderation-appeal")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/moderation/guidelines").hasAuthority("SCOPE_social:moderation-guidelines")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/safety/emergency-contact").hasAuthority("SCOPE_social:safety-contact")
                
                // Analytics & Insights
                .requestMatchers(HttpMethod.GET, "/api/v1/social/analytics/social-score").hasAuthority("SCOPE_social:analytics-score")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/analytics/engagement").hasAuthority("SCOPE_social:analytics-engagement")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/analytics/network-insights").hasAuthority("SCOPE_social:analytics-network")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/analytics/activity-summary").hasAuthority("SCOPE_social:analytics-activity")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/recommendations/people").hasAuthority("SCOPE_social:recommendations-people")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/recommendations/groups").hasAuthority("SCOPE_social:recommendations-groups")
                
                // Notifications & Preferences
                .requestMatchers(HttpMethod.GET, "/api/v1/social/notifications").hasAuthority("SCOPE_social:notifications-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/notifications/*/mark-read").hasAuthority("SCOPE_social:notifications-mark-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/social/notifications/preferences").hasAuthority("SCOPE_social:notification-preferences")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/notifications/subscribe").hasAuthority("SCOPE_social:notifications-subscribe")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/notifications/unsubscribe").hasAuthority("SCOPE_social:notifications-unsubscribe")
                
                // Social Media Integration
                .requestMatchers(HttpMethod.POST, "/api/v1/social/integration/connect/*").hasAuthority("SCOPE_social:integration-connect")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/social/integration/disconnect/*").hasAuthority("SCOPE_social:integration-disconnect")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/integration/status").hasAuthority("SCOPE_social:integration-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/integration/sync").hasAuthority("SCOPE_social:integration-sync")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/integration/share-to/*").hasAuthority("SCOPE_social:integration-share")
                
                // Admin Operations - Content Moderation
                .requestMatchers(HttpMethod.GET, "/api/v1/social/admin/moderation/queue").hasRole("CONTENT_MODERATOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/admin/moderation/*/approve").hasRole("CONTENT_MODERATOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/admin/moderation/*/reject").hasRole("CONTENT_MODERATOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/admin/moderation/*/flag").hasRole("CONTENT_MODERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/admin/reports/pending").hasRole("CONTENT_MODERATOR")
                
                // Admin Operations - User Management
                .requestMatchers(HttpMethod.GET, "/api/v1/social/admin/users/flagged").hasRole("SOCIAL_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/admin/users/*/suspend").hasRole("SOCIAL_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/admin/users/*/unsuspend").hasRole("SOCIAL_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/admin/users/*/restrict").hasRole("SOCIAL_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/admin/analytics/platform").hasRole("SOCIAL_ANALYST")
                
                // Admin Operations - System Management
                .requestMatchers("/api/v1/social/admin/**").hasRole("SOCIAL_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/admin/system/health").hasRole("SOCIAL_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/admin/system/maintenance").hasRole("SOCIAL_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/social/admin/audit/logs").hasRole("AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/social/admin/bulk-operations").hasRole("SOCIAL_ADMIN")
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/social/**").hasRole("SERVICE")
                .requestMatchers("/internal/connections/**").hasRole("SERVICE")
                .requestMatchers("/internal/social-payments/**").hasRole("SERVICE")
                .requestMatchers("/internal/gamification/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}