package com.waqiti.user.service;

import com.waqiti.user.client.IntegrationServiceClient;
import com.waqiti.user.client.dto.CreateUserResponse;
import com.waqiti.user.domain.*;
import com.waqiti.user.dto.*;
import com.waqiti.user.repository.*;
import com.waqiti.user.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-secret-key-must-be-at-least-32-characters-long-for-security",
        "jwt.access-token-validity=3600",
        "jwt.refresh-token-validity=86400"
})
@DisplayName("User Authentication Tests")
class UserAuthenticationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository profileRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private IntegrationServiceClient integrationClient;

    private String testUsername;
    private String testEmail;
    private String testPassword;
    private String testPhoneNumber;

    @BeforeEach
    void setUp() {
        testUsername = "testuser_" + UUID.randomUUID().toString().substring(0, 8);
        testEmail = testUsername + "@example.com";
        testPassword = "SecureP@ssw0rd123";
        testPhoneNumber = "+1234567890";

        when(integrationClient.createUser(any())).thenReturn(
                CreateUserResponse.builder()
                        .externalId(UUID.randomUUID().toString())
                        .success(true)
                        .build()
        );
    }

    @Nested
    @DisplayName("User Registration Tests")
    class UserRegistrationTests {

        @Test
        @Transactional
        @DisplayName("Should register new user successfully")
        void shouldRegisterNewUserSuccessfully() {
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .phoneNumber(testPhoneNumber)
                    .build();

            UserResponse response = userService.registerUser(request);

            assertThat(response).isNotNull();
            assertThat(response.getUsername()).isEqualTo(testUsername);
            assertThat(response.getEmail()).isEqualTo(testEmail);
            assertThat(response.getPhoneNumber()).isEqualTo(testPhoneNumber);
            assertThat(response.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION.toString());

            User savedUser = userRepository.findByUsername(testUsername).orElseThrow();
            assertThat(savedUser.getPasswordHash()).isNotNull();
            assertThat(passwordEncoder.matches(testPassword, savedUser.getPasswordHash())).isTrue();
            assertThat(savedUser.isActive()).isFalse();
        }

        @Test
        @Transactional
        @DisplayName("Should reject duplicate username")
        void shouldRejectDuplicateUsername() {
            UserRegistrationRequest request1 = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            userService.registerUser(request1);

            UserRegistrationRequest request2 = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email("different@example.com")
                    .password(testPassword)
                    .build();

            assertThatThrownBy(() -> userService.registerUser(request2))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("Username already exists");
        }

        @Test
        @Transactional
        @DisplayName("Should reject duplicate email")
        void shouldRejectDuplicateEmail() {
            UserRegistrationRequest request1 = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            userService.registerUser(request1);

            UserRegistrationRequest request2 = UserRegistrationRequest.builder()
                    .username("differentuser")
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            assertThatThrownBy(() -> userService.registerUser(request2))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("Email already exists");
        }

        @Test
        @Transactional
        @DisplayName("Should reject duplicate phone number")
        void shouldRejectDuplicatePhoneNumber() {
            UserRegistrationRequest request1 = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .phoneNumber(testPhoneNumber)
                    .build();

            userService.registerUser(request1);

            UserRegistrationRequest request2 = UserRegistrationRequest.builder()
                    .username("differentuser")
                    .email("different@example.com")
                    .password(testPassword)
                    .phoneNumber(testPhoneNumber)
                    .build();

            assertThatThrownBy(() -> userService.registerUser(request2))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("Phone number already exists");
        }

        @Test
        @Transactional
        @DisplayName("Should hash password securely")
        void shouldHashPasswordSecurely() {
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            userService.registerUser(request);

            User savedUser = userRepository.findByUsername(testUsername).orElseThrow();

            assertThat(savedUser.getPasswordHash()).isNotEqualTo(testPassword);
            assertThat(savedUser.getPasswordHash()).startsWith("$2a$");
            assertThat(passwordEncoder.matches(testPassword, savedUser.getPasswordHash())).isTrue();
            assertThat(passwordEncoder.matches("wrongpassword", savedUser.getPasswordHash())).isFalse();
        }

        @Test
        @Transactional
        @DisplayName("Should generate verification token on registration")
        void shouldGenerateVerificationTokenOnRegistration() {
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            UserResponse response = userService.registerUser(request);

            List<VerificationToken> tokens = tokenRepository.findByUserId(response.getId());
            assertThat(tokens).isNotEmpty();
            assertThat(tokens).anyMatch(token -> token.getType() == VerificationType.EMAIL);
        }

        @Test
        @Transactional
        @DisplayName("Should create user profile on registration")
        void shouldCreateUserProfileOnRegistration() {
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            UserResponse response = userService.registerUser(request);

            Optional<UserProfile> profile = profileRepository.findById(response.getId());
            assertThat(profile).isPresent();
        }
    }

    @Nested
    @DisplayName("User Authentication Tests")
    class UserAuthenticationTests {

        @Test
        @Transactional
        @DisplayName("Should authenticate user with valid credentials")
        void shouldAuthenticateUserWithValidCredentials() {
            User user = createAndActivateUser();

            org.springframework.security.core.userdetails.User userDetails =
                    new org.springframework.security.core.userdetails.User(
                            user.getUsername(),
                            user.getPasswordHash(),
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, testPassword, userDetails.getAuthorities());

            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(tokenProvider.createAccessToken(any(), anyString(), any())).thenReturn("access-token");
            when(tokenProvider.createRefreshToken(any(), anyString())).thenReturn("refresh-token");
            when(tokenProvider.getAccessTokenValidityInSeconds()).thenReturn(3600L);

            AuthenticationRequest request = AuthenticationRequest.builder()
                    .usernameOrEmail(user.getUsername())
                    .password(testPassword)
                    .build();

            AuthenticationResponse response = userService.authenticateUser(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(3600L);
            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getUsername()).isEqualTo(user.getUsername());
        }

        @Test
        @Transactional
        @DisplayName("Should reject authentication with invalid password")
        void shouldRejectAuthenticationWithInvalidPassword() {
            User user = createAndActivateUser();

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            AuthenticationRequest request = AuthenticationRequest.builder()
                    .usernameOrEmail(user.getUsername())
                    .password("WrongPassword123")
                    .build();

            assertThatThrownBy(() -> userService.authenticateUser(request))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @Transactional
        @DisplayName("Should reject authentication for non-existent user")
        void shouldRejectAuthenticationForNonExistentUser() {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            AuthenticationRequest request = AuthenticationRequest.builder()
                    .usernameOrEmail("nonexistent@example.com")
                    .password(testPassword)
                    .build();

            assertThatThrownBy(() -> userService.authenticateUser(request))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @Transactional
        @DisplayName("Should reject authentication for inactive user")
        void shouldRejectAuthenticationForInactiveUser() {
            UserRegistrationRequest regRequest = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            UserResponse registeredUser = userService.registerUser(regRequest);
            User user = userRepository.findById(registeredUser.getId()).orElseThrow();

            org.springframework.security.core.userdetails.User userDetails =
                    new org.springframework.security.core.userdetails.User(
                            user.getUsername(),
                            user.getPasswordHash(),
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, testPassword, userDetails.getAuthorities());

            when(authenticationManager.authenticate(any())).thenReturn(authentication);

            AuthenticationRequest request = AuthenticationRequest.builder()
                    .usernameOrEmail(user.getUsername())
                    .password(testPassword)
                    .build();

            assertThatThrownBy(() -> userService.authenticateUser(request))
                    .isInstanceOf(InvalidUserStateException.class)
                    .hasMessageContaining("not active");
        }

        @Test
        @Transactional
        @DisplayName("Should authenticate with email instead of username")
        void shouldAuthenticateWithEmailInsteadOfUsername() {
            User user = createAndActivateUser();

            org.springframework.security.core.userdetails.User userDetails =
                    new org.springframework.security.core.userdetails.User(
                            user.getUsername(),
                            user.getPasswordHash(),
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, testPassword, userDetails.getAuthorities());

            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(tokenProvider.createAccessToken(any(), anyString(), any())).thenReturn("access-token");
            when(tokenProvider.createRefreshToken(any(), anyString())).thenReturn("refresh-token");
            when(tokenProvider.getAccessTokenValidityInSeconds()).thenReturn(3600L);

            AuthenticationRequest request = AuthenticationRequest.builder()
                    .usernameOrEmail(user.getEmail())
                    .password(testPassword)
                    .build();

            AuthenticationResponse response = userService.authenticateUser(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Email Verification Tests")
    class EmailVerificationTests {

        @Test
        @Transactional
        @DisplayName("Should verify email with valid token")
        void shouldVerifyEmailWithValidToken() {
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            UserResponse response = userService.registerUser(request);

            List<VerificationToken> tokens = tokenRepository.findByUserId(response.getId());
            VerificationToken emailToken = tokens.stream()
                    .filter(token -> token.getType() == VerificationType.EMAIL)
                    .findFirst()
                    .orElseThrow();

            boolean verified = userService.verifyToken(emailToken.getToken(), VerificationType.EMAIL);

            assertThat(verified).isTrue();

            User user = userRepository.findById(response.getId()).orElseThrow();
            assertThat(user.isActive()).isTrue();
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

            VerificationToken usedToken = tokenRepository.findByToken(emailToken.getToken()).orElseThrow();
            assertThat(usedToken.isUsed()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should reject invalid verification token")
        void shouldRejectInvalidVerificationToken() {
            String invalidToken = UUID.randomUUID().toString();

            assertThatThrownBy(() -> userService.verifyToken(invalidToken, VerificationType.EMAIL))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessageContaining("Invalid token");
        }

        @Test
        @Transactional
        @DisplayName("Should reject already used token")
        void shouldRejectAlreadyUsedToken() {
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            UserResponse response = userService.registerUser(request);

            List<VerificationToken> tokens = tokenRepository.findByUserId(response.getId());
            VerificationToken emailToken = tokens.stream()
                    .filter(token -> token.getType() == VerificationType.EMAIL)
                    .findFirst()
                    .orElseThrow();

            userService.verifyToken(emailToken.getToken(), VerificationType.EMAIL);

            assertThatThrownBy(() -> userService.verifyToken(emailToken.getToken(), VerificationType.EMAIL))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessageContaining("expired or already used");
        }

        @Test
        @Transactional
        @DisplayName("Should reject token with wrong type")
        void shouldRejectTokenWithWrongType() {
            UserRegistrationRequest request = UserRegistrationRequest.builder()
                    .username(testUsername)
                    .email(testEmail)
                    .password(testPassword)
                    .build();

            UserResponse response = userService.registerUser(request);

            List<VerificationToken> tokens = tokenRepository.findByUserId(response.getId());
            VerificationToken emailToken = tokens.stream()
                    .filter(token -> token.getType() == VerificationType.EMAIL)
                    .findFirst()
                    .orElseThrow();

            assertThatThrownBy(() -> userService.verifyToken(emailToken.getToken(), VerificationType.PASSWORD_RESET))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessageContaining("not of the required type");
        }
    }

    @Nested
    @DisplayName("Password Management Tests")
    class PasswordManagementTests {

        @Test
        @Transactional
        @DisplayName("Should change password with correct current password")
        void shouldChangePasswordWithCorrectCurrentPassword() {
            User user = createAndActivateUser();
            String newPassword = "NewSecureP@ssw0rd456";

            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword(testPassword)
                    .newPassword(newPassword)
                    .build();

            boolean changed = userService.changePassword(user.getId(), request);

            assertThat(changed).isTrue();

            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(passwordEncoder.matches(testPassword, updatedUser.getPasswordHash())).isFalse();
            assertThat(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash())).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should reject password change with incorrect current password")
        void shouldRejectPasswordChangeWithIncorrectCurrentPassword() {
            User user = createAndActivateUser();
            String newPassword = "NewSecureP@ssw0rd456";

            PasswordChangeRequest request = PasswordChangeRequest.builder()
                    .currentPassword("WrongPassword123")
                    .newPassword(newPassword)
                    .build();

            assertThatThrownBy(() -> userService.changePassword(user.getId(), request))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("Current password is incorrect");
        }

        @Test
        @Transactional
        @DisplayName("Should initiate password reset with valid email")
        void shouldInitiatePasswordResetWithValidEmail() {
            User user = createAndActivateUser();

            PasswordResetInitiationRequest request = PasswordResetInitiationRequest.builder()
                    .email(user.getEmail())
                    .build();

            boolean initiated = userService.initiatePasswordReset(request);

            assertThat(initiated).isTrue();

            List<VerificationToken> tokens = tokenRepository.findByUserId(user.getId());
            assertThat(tokens).anyMatch(token ->
                    token.getType() == VerificationType.PASSWORD_RESET && token.isValid());
        }

        @Test
        @Transactional
        @DisplayName("Should reset password with valid token")
        void shouldResetPasswordWithValidToken() {
            User user = createAndActivateUser();

            String resetToken = userService.generateVerificationToken(
                    user.getId(), VerificationType.PASSWORD_RESET);

            String newPassword = "ResetP@ssw0rd789";
            PasswordResetRequest request = PasswordResetRequest.builder()
                    .token(resetToken)
                    .newPassword(newPassword)
                    .build();

            boolean reset = userService.resetPassword(request);

            assertThat(reset).isTrue();

            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash())).isTrue();
            assertThat(passwordEncoder.matches(testPassword, updatedUser.getPasswordHash())).isFalse();

            VerificationToken usedToken = tokenRepository.findByToken(resetToken).orElseThrow();
            assertThat(usedToken.isUsed()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should reject password reset with invalid token")
        void shouldRejectPasswordResetWithInvalidToken() {
            String invalidToken = UUID.randomUUID().toString();
            String newPassword = "ResetP@ssw0rd789";

            PasswordResetRequest request = PasswordResetRequest.builder()
                    .token(invalidToken)
                    .newPassword(newPassword)
                    .build();

            assertThatThrownBy(() -> userService.resetPassword(request))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessageContaining("Invalid token");
        }

        @Test
        @Transactional
        @DisplayName("Should reject password reset with already used token")
        void shouldRejectPasswordResetWithAlreadyUsedToken() {
            User user = createAndActivateUser();

            String resetToken = userService.generateVerificationToken(
                    user.getId(), VerificationType.PASSWORD_RESET);

            String newPassword = "ResetP@ssw0rd789";
            PasswordResetRequest request = PasswordResetRequest.builder()
                    .token(resetToken)
                    .newPassword(newPassword)
                    .build();

            userService.resetPassword(request);

            assertThatThrownBy(() -> userService.resetPassword(request))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessageContaining("expired or already used");
        }
    }

    @Nested
    @DisplayName("Account Security Tests")
    class AccountSecurityTests {

        @Test
        @Transactional
        @DisplayName("Should prevent brute force attacks with account lockout")
        void shouldPreventBruteForceAttacksWithAccountLockout() {
            User user = createAndActivateUser();

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            AuthenticationRequest request = AuthenticationRequest.builder()
                    .usernameOrEmail(user.getUsername())
                    .password("WrongPassword123")
                    .build();

            for (int i = 0; i < 5; i++) {
                try {
                    userService.authenticateUser(request);
                } catch (BadCredentialsException e) {
                    // Expected
                }
            }
        }

        @Test
        @Transactional
        @DisplayName("Should store password hash securely with BCrypt")
        void shouldStorePasswordHashSecurelyWithBCrypt() {
            User user = createAndActivateUser();

            assertThat(user.getPasswordHash()).isNotEqualTo(testPassword);
            assertThat(user.getPasswordHash()).startsWith("$2a$");
            assertThat(user.getPasswordHash().length()).isGreaterThan(50);
        }

        @Test
        @Transactional
        @DisplayName("Should generate unique verification tokens")
        void shouldGenerateUniqueVerificationTokens() {
            User user = createAndActivateUser();

            String token1 = userService.generateVerificationToken(user.getId(), VerificationType.EMAIL);
            String token2 = userService.generateVerificationToken(user.getId(), VerificationType.EMAIL);

            assertThat(token1).isNotEqualTo(token2);
            assertThat(token1).matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
            assertThat(token2).matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        }

        @Test
        @Transactional
        @DisplayName("Should enforce token expiration")
        void shouldEnforceTokenExpiration() {
            User user = createAndActivateUser();

            String token = userService.generateVerificationToken(user.getId(), VerificationType.PASSWORD_RESET);

            VerificationToken verificationToken = tokenRepository.findByToken(token).orElseThrow();

            assertThat(verificationToken.getExpiresAt()).isAfter(LocalDateTime.now());
            assertThat(verificationToken.getExpiresAt()).isBefore(LocalDateTime.now().plusDays(31));
        }
    }

    private User createAndActivateUser() {
        UserRegistrationRequest request = UserRegistrationRequest.builder()
                .username(testUsername)
                .email(testEmail)
                .password(testPassword)
                .phoneNumber(testPhoneNumber)
                .build();

        UserResponse response = userService.registerUser(request);
        User user = userRepository.findById(response.getId()).orElseThrow();
        user.activate();
        return userRepository.save(user);
    }
}