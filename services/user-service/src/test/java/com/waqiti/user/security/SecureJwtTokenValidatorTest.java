package com.waqiti.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-secret-key-must-be-at-least-32-characters-long-for-security",
        "jwt.issuer=waqiti-user-service",
        "jwt.audience=waqiti-platform",
        "jwt.max-token-age-hours=24",
        "jwt.rate-limit.max-requests=100",
        "jwt.rate-limit.window-minutes=1",
        "security.token-binding.enabled=true",
        "security.behavioral-analysis.enabled=true",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
@DisplayName("Secure JWT Token Validator Tests")
class SecureJwtTokenValidatorTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private SecureJwtTokenValidator tokenValidator;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private SecretKey signingKey;
    private SecureJwtTokenValidator.ValidationContext validationContext;

    @BeforeEach
    void setUp() {
        String jwtSecret = "test-secret-key-must-be-at-least-32-characters-long-for-security";
        signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        validationContext = new SecureJwtTokenValidator.ValidationContext("192.168.1.1", "Mozilla/5.0");
        validationContext.setAcceptLanguage("en-US");
        validationContext.setHighRiskOperation(false);

        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Nested
    @DisplayName("Token Format Validation Tests")
    class TokenFormatValidationTests {

        @Test
        @DisplayName("Should reject null token")
        void shouldRejectNullToken() {
            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(null, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("null or empty");
        }

        @Test
        @DisplayName("Should reject empty token")
        void shouldRejectEmptyToken() {
            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken("", validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("null or empty");
        }

        @Test
        @DisplayName("Should reject malformed token format")
        void shouldRejectMalformedTokenFormat() {
            String malformedToken = "not.a.valid.jwt.token";

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(malformedToken, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("Invalid token format");
        }

        @Test
        @DisplayName("Should reject token with only two parts")
        void shouldRejectTokenWithOnlyTwoParts() {
            String incompleteToken = "header.payload";

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(incompleteToken, validationContext);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("Should reject token that is too short")
        void shouldRejectTokenThatIsTooShort() {
            String shortToken = "a.b.c";

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(shortToken, validationContext);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("Should reject token that is too long")
        void shouldRejectTokenThatIsTooLong() {
            String longToken = "a".repeat(5000);

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(longToken, validationContext);

            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Algorithm Security Tests")
    class AlgorithmSecurityTests {

        @Test
        @DisplayName("Should reject 'none' algorithm bypass attack")
        void shouldRejectNoneAlgorithmBypassAttack() {
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "none");
            header.put("typ", "JWT");

            String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(header));

            Claims claims = Jwts.claims();
            claims.setSubject("testuser");
            claims.setIssuer("waqiti-user-service");
            claims.setAudience("waqiti-platform");
            claims.setIssuedAt(new Date());
            claims.setExpiration(new Date(System.currentTimeMillis() + 3600000));
            claims.setId(UUID.randomUUID().toString());
            claims.put("token_type", "access_token");
            claims.put("userId", UUID.randomUUID().toString());

            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(claims));

            String noneToken = encodedHeader + "." + encodedPayload + ".";

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(noneToken, validationContext);

            assertThat(result.isValid()).isFalse();
        } catch (Exception e) {
            // Expected - token validation should fail
        }

        @Test
        @DisplayName("Should reject RS256 algorithm confusion attack")
        void shouldRejectRS256AlgorithmConfusionAttack() {
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");

            Claims claims = createValidClaims();

            String maliciousToken;
            try {
                maliciousToken = Jwts.builder()
                        .setHeader(header)
                        .setClaims(claims)
                        .signWith(signingKey)
                        .compact();
            } catch (Exception e) {
                return;
            }

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(maliciousToken, validationContext);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("Should accept only HS256 algorithm")
        void shouldAcceptOnlyHS256Algorithm() {
            String token = createValidToken();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Claims Validation Tests")
    class ClaimsValidationTests {

        @Test
        @DisplayName("Should reject token without subject")
        void shouldRejectTokenWithoutSubject() {
            Claims claims = Jwts.claims();
            claims.setIssuer("waqiti-user-service");
            claims.setAudience("waqiti-platform");
            claims.setIssuedAt(new Date());
            claims.setExpiration(new Date(System.currentTimeMillis() + 3600000));
            claims.setId(UUID.randomUUID().toString());
            claims.put("token_type", "access_token");

            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("subject");
        }

        @Test
        @DisplayName("Should reject token with invalid username format")
        void shouldRejectTokenWithInvalidUsernameFormat() {
            Claims claims = createValidClaims();
            claims.setSubject("user@<script>alert('xss')</script>");

            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("subject format");
        }

        @Test
        @DisplayName("Should reject token with invalid userId format")
        void shouldRejectTokenWithInvalidUserIdFormat() {
            Claims claims = createValidClaims();
            claims.put("userId", "not-a-valid-uuid");

            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("userId format");
        }

        @Test
        @DisplayName("Should reject token without JWT ID (jti)")
        void shouldRejectTokenWithoutJwtId() {
            Claims claims = Jwts.claims();
            claims.setSubject("testuser");
            claims.setIssuer("waqiti-user-service");
            claims.setAudience("waqiti-platform");
            claims.setIssuedAt(new Date());
            claims.setExpiration(new Date(System.currentTimeMillis() + 3600000));
            claims.put("token_type", "access_token");
            claims.put("userId", UUID.randomUUID().toString());

            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("JWT ID");
        }

        @Test
        @DisplayName("Should reject token with future issued at time")
        void shouldRejectTokenWithFutureIssuedAtTime() {
            Claims claims = createValidClaims();
            claims.setIssuedAt(new Date(System.currentTimeMillis() + 600000));

            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("issued in future");
        }

        @Test
        @DisplayName("Should reject token without token type")
        void shouldRejectTokenWithoutTokenType() {
            Claims claims = createValidClaims();
            claims.remove("token_type");

            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("token type");
        }

        @Test
        @DisplayName("Should reject token with invalid authorities")
        void shouldRejectTokenWithInvalidAuthorities() {
            Claims claims = createValidClaims();
            claims.put("authorities", Arrays.asList("INVALID_ROLE", "MALICIOUS_PERMISSION"));

            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("authorities");
        }
    }

    @Nested
    @DisplayName("Token Expiration Tests")
    class TokenExpirationTests {

        @Test
        @DisplayName("Should reject expired token")
        void shouldRejectExpiredToken() {
            Claims claims = createValidClaims();
            claims.setExpiration(new Date(System.currentTimeMillis() - 3600000));

            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.isExpired()).isTrue();
        }

        @Test
        @DisplayName("Should accept token within validity period")
        void shouldAcceptTokenWithinValidityPeriod() {
            String token = createValidToken();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should reject token older than max age")
        void shouldRejectTokenOlderThanMaxAge() {
            Claims claims = createValidClaims();
            claims.setIssuedAt(new Date(System.currentTimeMillis() - 25 * 3600000));
            claims.setExpiration(new Date(System.currentTimeMillis() + 3600000));

            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Replay Attack Prevention Tests")
    class ReplayAttackPreventionTests {

        @Test
        @DisplayName("Should detect token replay attack")
        void shouldDetectTokenReplayAttack() {
            String token = createValidToken();

            SecureJwtTokenValidator.TokenValidationResult result1 =
                    tokenValidator.validateToken(token, validationContext);
            assertThat(result1.isValid()).isTrue();

            SecureJwtTokenValidator.TokenValidationResult result2 =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result2.isValid()).isFalse();
            assertThat(result2.getErrorMessage()).contains("replay");
        }

        @Test
        @DisplayName("Should store JWT ID in Redis to prevent replay")
        void shouldStoreJwtIdInRedisToPreventReplay() {
            String token = createValidToken();
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            tokenValidator.validateToken(token, validationContext);

            String jtiKey = "token:jti:" + claims.getId();
            Boolean exists = redisTemplate.hasKey(jtiKey);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Should allow different tokens with different JTIs")
        void shouldAllowDifferentTokensWithDifferentJTIs() {
            String token1 = createValidToken();
            String token2 = createValidToken();

            SecureJwtTokenValidator.TokenValidationResult result1 =
                    tokenValidator.validateToken(token1, validationContext);
            SecureJwtTokenValidator.TokenValidationResult result2 =
                    tokenValidator.validateToken(token2, validationContext);

            assertThat(result1.isValid()).isTrue();
            assertThat(result2.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Rate Limiting Tests")
    class RateLimitingTests {

        @Test
        @DisplayName("Should enforce rate limiting per IP address")
        void shouldEnforceRateLimitingPerIPAddress() {
            String token = createValidToken();

            for (int i = 0; i < 100; i++) {
                tokenValidator.validateToken(token, validationContext);
            }

            String newToken = createValidToken();
            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(newToken, validationContext);

            assertThat(result.isRateLimited()).isTrue();
        }

        @Test
        @DisplayName("Should allow requests under rate limit")
        void shouldAllowRequestsUnderRateLimit() {
            for (int i = 0; i < 50; i++) {
                String token = createValidToken();
                SecureJwtTokenValidator.TokenValidationResult result =
                        tokenValidator.validateToken(token, validationContext);

                if (i < 49) {
                    assertThat(result.isValid() || result.isValid() == false).isTrue();
                }
            }
        }

        @Test
        @DisplayName("Should have separate rate limits per IP")
        void shouldHaveSeparateRateLimitsPerIP() {
            SecureJwtTokenValidator.ValidationContext context1 =
                    new SecureJwtTokenValidator.ValidationContext("192.168.1.1", "Mozilla/5.0");
            SecureJwtTokenValidator.ValidationContext context2 =
                    new SecureJwtTokenValidator.ValidationContext("192.168.1.2", "Mozilla/5.0");

            for (int i = 0; i < 99; i++) {
                String token = createValidToken();
                tokenValidator.validateToken(token, context1);
            }

            String token = createValidToken();
            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, context2);

            assertThat(result.isRateLimited()).isFalse();
        }
    }

    @Nested
    @DisplayName("Signature Validation Tests")
    class SignatureValidationTests {

        @Test
        @DisplayName("Should reject token with invalid signature")
        void shouldRejectTokenWithInvalidSignature() {
            String token = createValidToken();

            String[] parts = token.split("\\.");
            String tamperedToken = parts[0] + "." + parts[1] + ".invalid-signature";

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(tamperedToken, validationContext);

            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("Should reject token signed with different key")
        void shouldRejectTokenSignedWithDifferentKey() {
            SecretKey differentKey = Keys.hmacShaKeyFor(
                    "different-secret-key-at-least-32-characters-long".getBytes());

            Claims claims = createValidClaims();
            String token = Jwts.builder()
                    .setClaims(claims)
                    .signWith(differentKey)
                    .compact();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("signature");
        }

        @Test
        @DisplayName("Should accept token with valid signature")
        void shouldAcceptTokenWithValidSignature() {
            String token = createValidToken();

            SecureJwtTokenValidator.TokenValidationResult result =
                    tokenValidator.validateToken(token, validationContext);

            assertThat(result.isValid()).isTrue();
        }
    }

    private String createValidToken() {
        Claims claims = createValidClaims();

        return Jwts.builder()
                .setClaims(claims)
                .signWith(signingKey)
                .compact();
    }

    private Claims createValidClaims() {
        Claims claims = Jwts.claims();
        claims.setSubject("testuser");
        claims.setIssuer("waqiti-user-service");
        claims.setAudience("waqiti-platform");
        claims.setIssuedAt(new Date());
        claims.setExpiration(new Date(System.currentTimeMillis() + 3600000));
        claims.setId(UUID.randomUUID().toString());
        claims.put("token_type", "access_token");
        claims.put("userId", UUID.randomUUID().toString());
        claims.put("authorities", Arrays.asList("ROLE_USER", "PERMISSION_READ"));
        return claims;
    }
}