package com.waqiti.investment.config;

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
 * Keycloak security configuration for Investment Service
 * Manages authentication and authorization for investment and trading operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class InvestmentKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain investmentKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Investment Service");
        
        return createKeycloakSecurityFilterChain(http, "investment-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/investment/public/**").permitAll()
                .requestMatchers("/api/v1/investment/markets/status").permitAll() // Public market status
                .requestMatchers("/api/v1/investment/quotes/public/**").permitAll() // Public quotes
                
                // Account Management
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/accounts/create").hasAuthority("SCOPE_investment:account-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/accounts").hasAuthority("SCOPE_investment:account-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/accounts/*").hasAuthority("SCOPE_investment:account-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/investment/accounts/*").hasAuthority("SCOPE_investment:account-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/accounts/*/activate").hasAuthority("SCOPE_investment:account-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/accounts/*/close").hasAuthority("SCOPE_investment:account-close")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/accounts/*/balance").hasAuthority("SCOPE_investment:balance-view")
                
                // Portfolio Management
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/portfolio").hasAuthority("SCOPE_investment:portfolio-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/portfolio/performance").hasAuthority("SCOPE_investment:performance-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/portfolio/holdings").hasAuthority("SCOPE_investment:holdings-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/portfolio/allocation").hasAuthority("SCOPE_investment:allocation-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/portfolio/rebalance").hasAuthority("SCOPE_investment:portfolio-rebalance")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/portfolio/analysis").hasAuthority("SCOPE_investment:portfolio-analysis")
                
                // Trading Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/orders/buy").hasAuthority("SCOPE_investment:order-buy")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/orders/sell").hasAuthority("SCOPE_investment:order-sell")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/orders").hasAuthority("SCOPE_investment:orders-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/orders/*").hasAuthority("SCOPE_investment:order-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/orders/*/cancel").hasAuthority("SCOPE_investment:order-cancel")
                .requestMatchers(HttpMethod.PUT, "/api/v1/investment/orders/*/modify").hasAuthority("SCOPE_investment:order-modify")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/orders/history").hasAuthority("SCOPE_investment:order-history")
                
                // Market Data
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/quotes/*").hasAuthority("SCOPE_investment:quotes-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/quotes/*/realtime").hasAuthority("SCOPE_investment:quotes-realtime")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/quotes/*/chart").hasAuthority("SCOPE_investment:chart-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/market/movers").hasAuthority("SCOPE_investment:market-movers")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/market/news").hasAuthority("SCOPE_investment:market-news")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/market/calendar").hasAuthority("SCOPE_investment:market-calendar")
                
                // Watchlists
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/watchlists/create").hasAuthority("SCOPE_investment:watchlist-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/watchlists").hasAuthority("SCOPE_investment:watchlist-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/investment/watchlists/*").hasAuthority("SCOPE_investment:watchlist-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/investment/watchlists/*").hasAuthority("SCOPE_investment:watchlist-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/watchlists/*/add").hasAuthority("SCOPE_investment:watchlist-add-symbol")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/investment/watchlists/*/remove/*").hasAuthority("SCOPE_investment:watchlist-remove-symbol")
                
                // Auto-Invest / Dollar Cost Averaging
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/auto-invest/create").hasAuthority("SCOPE_investment:auto-invest-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/auto-invest").hasAuthority("SCOPE_investment:auto-invest-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/investment/auto-invest/*").hasAuthority("SCOPE_investment:auto-invest-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/investment/auto-invest/*").hasAuthority("SCOPE_investment:auto-invest-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/auto-invest/*/pause").hasAuthority("SCOPE_investment:auto-invest-pause")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/auto-invest/*/resume").hasAuthority("SCOPE_investment:auto-invest-resume")
                
                // Robo-Advisor
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/robo-advisor/assess").hasAuthority("SCOPE_investment:robo-assess")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/robo-advisor/strategy").hasAuthority("SCOPE_investment:robo-strategy-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/robo-advisor/strategy/apply").hasAuthority("SCOPE_investment:robo-strategy-apply")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/robo-advisor/recommendations").hasAuthority("SCOPE_investment:robo-recommendations")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/robo-advisor/rebalance").hasAuthority("SCOPE_investment:robo-rebalance")
                
                // Research & Analysis
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/research/stocks/*").hasAuthority("SCOPE_investment:research-stocks")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/research/etfs/*").hasAuthority("SCOPE_investment:research-etfs")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/research/fundamentals/*").hasAuthority("SCOPE_investment:fundamentals-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/research/technicals/*").hasAuthority("SCOPE_investment:technicals-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/research/ratings/*").hasAuthority("SCOPE_investment:ratings-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/research/screener").hasAuthority("SCOPE_investment:screener-use")
                
                // Options Trading (Advanced)
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/options/trade").hasAuthority("SCOPE_investment:options-trade")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/options/chain/*").hasAuthority("SCOPE_investment:options-chain")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/options/strategies").hasAuthority("SCOPE_investment:options-strategies")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/options/exercise").hasAuthority("SCOPE_investment:options-exercise")
                
                // Tax & Reporting
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/tax/documents").hasAuthority("SCOPE_investment:tax-documents")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/tax/harvest").hasAuthority("SCOPE_investment:tax-harvest-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/tax/harvest/execute").hasAuthority("SCOPE_investment:tax-harvest-execute")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/statements").hasAuthority("SCOPE_investment:statements-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/confirmations").hasAuthority("SCOPE_investment:confirmations-view")
                
                // Transfers & Funding
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/transfers/deposit").hasAuthority("SCOPE_investment:transfer-deposit")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/transfers/withdraw").hasAuthority("SCOPE_investment:transfer-withdraw")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/transfers/history").hasAuthority("SCOPE_investment:transfer-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/transfers/ach/link").hasAuthority("SCOPE_investment:ach-link")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/transfers/wire/initiate").hasAuthority("SCOPE_investment:wire-transfer")
                
                // Alerts & Notifications
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/alerts/create").hasAuthority("SCOPE_investment:alerts-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/alerts").hasAuthority("SCOPE_investment:alerts-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/investment/alerts/*").hasAuthority("SCOPE_investment:alerts-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/investment/alerts/*").hasAuthority("SCOPE_investment:alerts-delete")
                
                // Dividends & Corporate Actions
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/dividends").hasAuthority("SCOPE_investment:dividends-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/dividends/reinvest").hasAuthority("SCOPE_investment:dividends-reinvest")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/corporate-actions").hasAuthority("SCOPE_investment:corporate-actions")
                
                // Margin Trading (Advanced)
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/margin/status").hasAuthority("SCOPE_investment:margin-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/margin/enable").hasAuthority("SCOPE_investment:margin-enable")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/margin/borrow").hasAuthority("SCOPE_investment:margin-borrow")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/margin/requirements").hasAuthority("SCOPE_investment:margin-requirements")
                
                // Admin Operations
                .requestMatchers("/api/v1/investment/admin/**").hasRole("INVESTMENT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/admin/accounts/freeze").hasRole("INVESTMENT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/admin/trading/halt").hasRole("INVESTMENT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/investment/admin/positions/all").hasRole("INVESTMENT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/investment/admin/orders/force-close").hasRole("INVESTMENT_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/investment/**").hasRole("SERVICE")
                .requestMatchers("/internal/trading/**").hasRole("SERVICE")
                .requestMatchers("/internal/portfolio/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}