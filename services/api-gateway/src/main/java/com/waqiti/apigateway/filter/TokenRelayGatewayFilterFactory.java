package com.waqiti.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import lombok.extern.slf4j.Slf4j;

/**
 * Gateway filter that relays the JWT token to downstream services
 * This ensures that the original user's authentication is preserved throughout the request chain
 */
@Slf4j
@Component
public class TokenRelayGatewayFilterFactory extends AbstractGatewayFilterFactory<TokenRelayGatewayFilterFactory.Config> {

    public TokenRelayGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> withBearerToken(exchange, jwt))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
        };
    }

    private ServerWebExchange withBearerToken(ServerWebExchange exchange, Jwt jwt) {
        String tokenValue = jwt.getTokenValue();
        log.debug("Relaying token to downstream service for user: {}", jwt.getSubject());
        
        return exchange.mutate()
            .request(r -> r.headers(headers -> {
                headers.setBearerAuth(tokenValue);
                // Also pass user information as headers for services that need it
                headers.add("X-User-Id", jwt.getSubject());
                if (jwt.hasClaim("preferred_username")) {
                    headers.add("X-User-Name", jwt.getClaimAsString("preferred_username"));
                }
                if (jwt.hasClaim("email")) {
                    headers.add("X-User-Email", jwt.getClaimAsString("email"));
                }
            }))
            .build();
    }

    public static class Config {
        // Configuration properties can be added here if needed
        private boolean includeUserHeaders = true;
        
        public boolean isIncludeUserHeaders() {
            return includeUserHeaders;
        }
        
        public void setIncludeUserHeaders(boolean includeUserHeaders) {
            this.includeUserHeaders = includeUserHeaders;
        }
    }
}