package com.waqiti.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecureTokenService {

    @Value("${app.cookie.domain:.waqiti.com}")
    private String cookieDomain;

    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    public void setTokenCookies(HttpServletResponse response,
                                String accessToken,
                                String refreshToken) {
        Cookie accessCookie = new Cookie("accessToken", accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(secureCookie);
        accessCookie.setPath("/");
        accessCookie.setDomain(cookieDomain);
        accessCookie.setMaxAge(900);
        accessCookie.setAttribute("SameSite", "Strict");
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(secureCookie);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setDomain(cookieDomain);
        refreshCookie.setMaxAge(604800);
        refreshCookie.setAttribute("SameSite", "Strict");
        response.addCookie(refreshCookie);
    }

    public void clearTokenCookies(HttpServletResponse response) {
        Cookie accessCookie = new Cookie("accessToken", "");
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie("refreshToken", "");
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);
    }
}
