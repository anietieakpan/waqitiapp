package com.waqiti.support.config;

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
 * Keycloak security configuration for Support Service
 * Manages authentication and authorization for customer support operations
 * Including ticket management, live chat, knowledge base, and AI-powered support
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class SupportKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain supportKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Support Service");
        
        return createKeycloakSecurityFilterChain(http, "support-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/support/public/**").permitAll()
                .requestMatchers("/api/v1/support/public/knowledge-base/search").permitAll()
                .requestMatchers("/api/v1/support/public/contact-info").permitAll()
                
                // Ticket Management
                .requestMatchers(HttpMethod.POST, "/api/v1/support/tickets/create").hasAuthority("SCOPE_support:ticket-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/tickets").hasAuthority("SCOPE_support:tickets-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/tickets/*").hasAuthority("SCOPE_support:ticket-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/support/tickets/*").hasAuthority("SCOPE_support:ticket-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/support/tickets/*").hasAuthority("SCOPE_support:ticket-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/tickets/*/close").hasAuthority("SCOPE_support:ticket-close")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/tickets/*/reopen").hasAuthority("SCOPE_support:ticket-reopen")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/tickets/*/escalate").hasAuthority("SCOPE_support:ticket-escalate")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/tickets/*/history").hasAuthority("SCOPE_support:ticket-history")
                
                // Ticket Comments & Communication
                .requestMatchers(HttpMethod.POST, "/api/v1/support/tickets/*/comments").hasAuthority("SCOPE_support:ticket-comment")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/tickets/*/comments").hasAuthority("SCOPE_support:ticket-comments-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/support/tickets/*/comments/*").hasAuthority("SCOPE_support:ticket-comment-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/support/tickets/*/comments/*").hasAuthority("SCOPE_support:ticket-comment-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/tickets/*/attachments").hasAuthority("SCOPE_support:ticket-attachment-upload")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/tickets/*/attachments/*").hasAuthority("SCOPE_support:ticket-attachment-download")
                
                // Live Chat Management
                .requestMatchers(HttpMethod.POST, "/api/v1/support/chat/start").hasAuthority("SCOPE_support:chat-start")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/chat/sessions").hasAuthority("SCOPE_support:chat-sessions-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/chat/sessions/*").hasAuthority("SCOPE_support:chat-session-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/chat/sessions/*/messages").hasAuthority("SCOPE_support:chat-message-send")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/chat/sessions/*/messages").hasAuthority("SCOPE_support:chat-messages-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/chat/sessions/*/end").hasAuthority("SCOPE_support:chat-end")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/chat/sessions/*/transfer").hasAuthority("SCOPE_support:chat-transfer")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/chat/sessions/*/typing").hasAuthority("SCOPE_support:chat-typing")
                
                // Knowledge Base - Customer Access
                .requestMatchers(HttpMethod.GET, "/api/v1/support/knowledge-base/search").hasAuthority("SCOPE_support:kb-search")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/knowledge-base/articles").hasAuthority("SCOPE_support:kb-articles-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/knowledge-base/articles/*").hasAuthority("SCOPE_support:kb-article-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/knowledge-base/articles/*/helpful").hasAuthority("SCOPE_support:kb-article-rate")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/knowledge-base/categories").hasAuthority("SCOPE_support:kb-categories-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/knowledge-base/popular").hasAuthority("SCOPE_support:kb-popular-view")
                
                // FAQ Management
                .requestMatchers(HttpMethod.GET, "/api/v1/support/faq").hasAuthority("SCOPE_support:faq-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/faq/categories").hasAuthority("SCOPE_support:faq-categories-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/faq/*/feedback").hasAuthority("SCOPE_support:faq-feedback")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/faq/search").hasAuthority("SCOPE_support:faq-search")
                
                // AI-Powered Support Features
                .requestMatchers(HttpMethod.POST, "/api/v1/support/ai/ask").hasAuthority("SCOPE_support:ai-ask")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/ai/analyze-sentiment").hasAuthority("SCOPE_support:ai-sentiment")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/ai/suggest-response").hasAuthority("SCOPE_support:ai-suggest")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/ai/classify-intent").hasAuthority("SCOPE_support:ai-classify")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/ai/suggestions/").hasAuthority("SCOPE_support:ai-suggestions")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/ai/feedback").hasAuthority("SCOPE_support:ai-feedback")
                
                // Customer Feedback & Surveys
                .requestMatchers(HttpMethod.POST, "/api/v1/support/feedback/submit").hasAuthority("SCOPE_support:feedback-submit")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/feedback/surveys").hasAuthority("SCOPE_support:surveys-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/feedback/surveys/*/respond").hasAuthority("SCOPE_support:survey-respond")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/feedback/history").hasAuthority("SCOPE_support:feedback-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/feedback/rating").hasAuthority("SCOPE_support:rating-submit")
                
                // Self-Service Portal
                .requestMatchers(HttpMethod.GET, "/api/v1/support/self-service/guides").hasAuthority("SCOPE_support:self-service-guides")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/self-service/troubleshooting").hasAuthority("SCOPE_support:troubleshooting-guides")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/self-service/password-reset").hasAuthority("SCOPE_support:password-reset-request")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/self-service/account-recovery").hasAuthority("SCOPE_support:account-recovery")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/self-service/account-status").hasAuthority("SCOPE_support:account-status")
                
                // Status & Service Health
                .requestMatchers(HttpMethod.GET, "/api/v1/support/status/system").hasAuthority("SCOPE_support:system-status")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/status/incidents").hasAuthority("SCOPE_support:incidents-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/status/maintenance").hasAuthority("SCOPE_support:maintenance-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/status/subscribe-updates").hasAuthority("SCOPE_support:status-subscribe")
                
                // Support Analytics (Customer View)
                .requestMatchers(HttpMethod.GET, "/api/v1/support/analytics/my-tickets").hasAuthority("SCOPE_support:analytics-my-tickets")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/analytics/resolution-times").hasAuthority("SCOPE_support:analytics-resolution-times")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/analytics/satisfaction").hasAuthority("SCOPE_support:analytics-satisfaction")
                
                // Agent Operations - Ticket Management
                .requestMatchers(HttpMethod.GET, "/api/v1/support/agent/tickets/assigned").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/agent/tickets/*/assign").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/agent/tickets/*/priority/change").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/agent/tickets/*/status/update").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/agent/queue").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/agent/availability/set").hasRole("SUPPORT_AGENT")
                
                // Agent Operations - Live Chat
                .requestMatchers(HttpMethod.GET, "/api/v1/support/agent/chat/active-sessions").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/agent/chat/sessions/*/join").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/agent/chat/sessions/*/notes").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/agent/chat/templates").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/agent/chat/canned-responses").hasRole("SUPPORT_AGENT")
                
                // Knowledge Base Management - Agent Access
                .requestMatchers(HttpMethod.POST, "/api/v1/support/agent/knowledge-base/articles").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.PUT, "/api/v1/support/agent/knowledge-base/articles/*").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/support/agent/knowledge-base/articles/*").hasRole("SUPPORT_AGENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/agent/knowledge-base/review").hasRole("SUPPORT_AGENT")
                
                // Supervisor Operations - Team Management
                .requestMatchers(HttpMethod.GET, "/api/v1/support/supervisor/team/performance").hasRole("SUPPORT_SUPERVISOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/supervisor/team/workload").hasRole("SUPPORT_SUPERVISOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/supervisor/team/assignments").hasRole("SUPPORT_SUPERVISOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/supervisor/escalations").hasRole("SUPPORT_SUPERVISOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/supervisor/quality/review").hasRole("SUPPORT_SUPERVISOR")
                
                // Admin Operations - System Configuration
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/settings").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/support/admin/settings").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/agents").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/agents").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/support/admin/agents/*").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/support/admin/agents/*").hasRole("SUPPORT_ADMIN")
                
                // Admin Operations - Analytics & Reporting
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/analytics/dashboard").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/analytics/performance").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/analytics/customer-satisfaction").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/reports/generate").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/reports/*/download").hasRole("SUPPORT_ADMIN")
                
                // Admin Operations - AI & ML Management\n                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/ai/models").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/support/admin/ai/settings").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/ai/retrain").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/ai/performance").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/ai/feedback-review").hasRole("SUPPORT_ADMIN")
                
                // Admin Operations - Integration Management
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/integrations").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/integrations").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/support/admin/integrations/*").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/support/admin/integrations/*").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/integrations/*/sync").hasRole("SUPPORT_ADMIN")
                
                // Admin Operations - Security & Audit
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/audit/logs").hasRole("AUDITOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/security/permissions").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/support/admin/security/permissions").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/compliance/reports").hasRole("COMPLIANCE_OFFICER")
                
                // Admin Operations - Data Management
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/data/backup").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/data/export").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/data/cleanup").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/data/statistics").hasRole("SUPPORT_ADMIN")
                
                // Admin Operations - General Management
                .requestMatchers("/api/v1/support/admin/**").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/support/admin/system/health").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/system/maintenance").hasRole("SUPPORT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/support/admin/bulk-operations").hasRole("SUPPORT_ADMIN")
                
                // WebSocket endpoints for real-time communication
                .requestMatchers("/ws/**").authenticated()
                .requestMatchers("/api/v1/support/ws/**").authenticated()
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/support/**").hasRole("SERVICE")
                .requestMatchers("/internal/tickets/**").hasRole("SERVICE")
                .requestMatchers("/internal/chat/**").hasRole("SERVICE")
                .requestMatchers("/internal/knowledge-base/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}