package com.waqiti.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SecurityCookieConfig {

    @Value("${app.cookie.domain:.waqiti.com}")
    private String cookieDomain;

    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("accessToken");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        serializer.setSameSite("Strict");
        serializer.setCookieMaxAge(900); // 15 minutes
        serializer.setDomainNamePattern("^.+?\\.?(.+)$");
        return serializer;
    }

    @Bean(name = "refreshTokenCookieSerializer")
    public CookieSerializer refreshTokenCookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("refreshToken");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        serializer.setSameSite("Strict");
        serializer.setCookieMaxAge(604800); // 7 days
        serializer.setCookiePath("/api/auth/refresh");
        return serializer;
    }
}
