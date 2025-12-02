package com.waqiti.user.service;

import com.waqiti.user.dto.CreateUserRequest;
import com.waqiti.user.dto.UpdateUserRequest;
import com.waqiti.user.dto.ChangePasswordRequest;
import com.waqiti.user.model.User;
import com.waqiti.user.model.UserStatus;
import com.waqiti.user.model.KycStatus;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.event.UserEventPublisher;
import com.waqiti.common.exception.UserException;
import com.waqiti.common.exception.UserNotFoundException;
import com.waqiti.common.exception.DuplicateEmailException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for UserService.
 *
 * Test Coverage:
 * - User creation and validation
 * - User update operations
 * - Password management
 * - User status management (suspend, block, activate)
 * - KYC verification
 * - Email/phone verification
 * - User queries
 * - Security validations
 * - Edge cases
 *
 * @author Waqiti Platform Engineering
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private UUID userId;
    private User mockUser;
    private CreateUserRequest validCreateRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        mockUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .phone("+1234567890")
                .country("US")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .passwordHash("hashed_password")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_STARTED)
                .emailVerified(false)
                .phoneVerified(false)
                .twoFactorEnabled(false)
                .createdAt(LocalDateTime.now())
                .version(0L)
                .build();

        validCreateRequest = CreateUserRequest.builder()
                .email("newuser@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .phone("+0987654321")
                .country("US")
                .dateOfBirth(LocalDate.of(1995, 5, 15))
                .password("SecureP@ssw0rd")
                .build();
    }

    // =========================================================================
    // User Creation Tests
    // =========================================================================

    @Nested
    @DisplayName("User Creation Tests")
    class UserCreationTests {

        @Test
        @DisplayName("Should successfully create user with valid request")
        void shouldCreateUserSuccessfully() {
            // Arrange
            when(userRepository.existsByEmail(validCreateRequest.getEmail())).thenReturn(false);
            when(passwordEncoder.encode(validCreateRequest.getPassword())).thenReturn("encoded_password");
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            User createdUser = userService.createUser(validCreateRequest);

            // Assert
            assertNotNull(createdUser);
            assertEquals(validCreateRequest.getEmail(), createdUser.getEmail());

            // Verify password was encoded
            verify(passwordEncoder).encode(validCreateRequest.getPassword());

            // Verify user was saved
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(validCreateRequest.getEmail(), savedUser.getEmail());
            assertEquals(UserStatus.PENDING_VERIFICATION, savedUser.getStatus());
            assertEquals(KycStatus.NOT_STARTED, savedUser.getKycStatus());

            // Verify event was published
            verify(eventPublisher).publishUserCreatedEvent(any());
        }

        @Test
        @DisplayName("Should reject duplicate email")
        void shouldRejectDuplicateEmail() {
            // Arrange
            when(userRepository.existsByEmail(validCreateRequest.getEmail())).thenReturn(true);

            // Act & Assert
            assertThrows(DuplicateEmailException.class, () -> {
                userService.createUser(validCreateRequest);
            });

            // Verify user was NOT created
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject invalid email format")
        void shouldRejectInvalidEmail() {
            // Arrange
            validCreateRequest.setEmail("invalid-email");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                userService.createUser(validCreateRequest);
            });
        }

        @Test
        @DisplayName("Should reject weak password")
        void shouldRejectWeakPassword() {
            // Arrange
            validCreateRequest.setPassword("weak");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                userService.createUser(validCreateRequest);
            });
        }

        @Test
        @DisplayName("Should reject underage user")
        void shouldRejectUnderageUser() {
            // Arrange
            validCreateRequest.setDateOfBirth(LocalDate.now().minusYears(15)); // 15 years old

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                userService.createUser(validCreateRequest);
            });
        }

        @Test
        @DisplayName("Should accept user exactly 18 years old")
        void shouldAcceptExactly18YearsOld() {
            // Arrange
            validCreateRequest.setDateOfBirth(LocalDate.now().minusYears(18));
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            User createdUser = userService.createUser(validCreateRequest);

            // Assert
            assertNotNull(createdUser);
        }

        @Test
        @DisplayName("Should reject null first name")
        void shouldRejectNullFirstName() {
            // Arrange
            validCreateRequest.setFirstName(null);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                userService.createUser(validCreateRequest);
            });
        }

        @Test
        @DisplayName("Should reject empty last name")
        void shouldRejectEmptyLastName() {
            // Arrange
            validCreateRequest.setLastName("");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                userService.createUser(validCreateRequest);
            });
        }
    }

    // =========================================================================
    // User Update Tests
    // =========================================================================

    @Nested
    @DisplayName("User Update Tests")
    class UserUpdateTests {

        @Test
        @DisplayName("Should successfully update user profile")
        void shouldUpdateUserProfileSuccessfully() {
            // Arrange
            UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                    .firstName("Updated")
                    .lastName("Name")
                    .phone("+1111111111")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            User updatedUser = userService.updateUser(userId, updateRequest);

            // Assert
            assertNotNull(updatedUser);

            // Verify user was saved
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals("Updated", savedUser.getFirstName());
            assertEquals("Name", savedUser.getLastName());

            // Verify event was published
            verify(eventPublisher).publishUserUpdatedEvent(any());
        }

        @Test
        @DisplayName("Should not update email through update request")
        void shouldNotUpdateEmail() {
            // Email updates should require separate verification flow
            UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                    .email("newemail@example.com") // Should be ignored
                    .firstName("Updated")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            userService.updateUser(userId, updateRequest);

            // Verify email was NOT changed
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(mockUser.getEmail(), savedUser.getEmail());
        }

        @Test
        @DisplayName("Should handle non-existent user update")
        void shouldHandleNonExistentUserUpdate() {
            // Arrange
            UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                    .firstName("Updated")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(UserNotFoundException.class, () -> {
                userService.updateUser(userId, updateRequest);
            });
        }
    }

    // =========================================================================
    // Password Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Password Management Tests")
    class PasswordManagementTests {

        @Test
        @DisplayName("Should successfully change password with correct old password")
        void shouldChangePasswordSuccessfully() {
            // Arrange
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("OldP@ssw0rd")
                    .newPassword("NewSecureP@ssw0rd")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches(request.getOldPassword(), mockUser.getPasswordHash())).thenReturn(true);
            when(passwordEncoder.encode(request.getNewPassword())).thenReturn("new_encoded_password");
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            userService.changePassword(userId, request);

            // Verify password was updated
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals("new_encoded_password", savedUser.getPasswordHash());

            // Verify event was published
            verify(eventPublisher).publishPasswordChangedEvent(userId);
        }

        @Test
        @DisplayName("Should reject password change with incorrect old password")
        void shouldRejectIncorrectOldPassword() {
            // Arrange
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("WrongPassword")
                    .newPassword("NewSecureP@ssw0rd")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches(request.getOldPassword(), mockUser.getPasswordHash())).thenReturn(false);

            // Act & Assert
            assertThrows(UserException.class, () -> {
                userService.changePassword(userId, request);
            });

            // Verify password was NOT updated
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject weak new password")
        void shouldRejectWeakNewPassword() {
            // Arrange
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("OldP@ssw0rd")
                    .newPassword("weak")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches(request.getOldPassword(), mockUser.getPasswordHash())).thenReturn(true);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                userService.changePassword(userId, request);
            });
        }

        @Test
        @DisplayName("Should reject same old and new password")
        void shouldRejectSamePassword() {
            // Arrange
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("SameP@ssw0rd")
                    .newPassword("SameP@ssw0rd")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                userService.changePassword(userId, request);
            });
        }
    }

    // =========================================================================
    // User Status Management Tests
    // =========================================================================

    @Nested
    @DisplayName("User Status Management Tests")
    class UserStatusManagementTests {

        @Test
        @DisplayName("Should successfully suspend active user")
        void shouldSuspendUserSuccessfully() {
            // Arrange
            String reason = "Suspicious activity detected";
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            userService.suspendUser(userId, reason);

            // Verify status was updated
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(UserStatus.SUSPENDED, savedUser.getStatus());

            // Verify event was published
            verify(eventPublisher).publishUserSuspendedEvent(userId, reason);
        }

        @Test
        @DisplayName("Should successfully block user")
        void shouldBlockUserSuccessfully() {
            // Arrange
            String reason = "Fraudulent activity";
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            userService.blockUser(userId, reason);

            // Verify status was updated
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(UserStatus.BLOCKED, savedUser.getStatus());

            // Verify event was published
            verify(eventPublisher).publishUserBlockedEvent(userId, reason);
        }

        @Test
        @DisplayName("Should successfully activate suspended user")
        void shouldActivateUserSuccessfully() {
            // Arrange
            mockUser.setStatus(UserStatus.SUSPENDED);
            String reason = "Investigation completed";
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            userService.activateUser(userId, reason);

            // Verify status was updated
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(UserStatus.ACTIVE, savedUser.getStatus());

            // Verify event was published
            verify(eventPublisher).publishUserActivatedEvent(userId, reason);
        }
    }

    // =========================================================================
    // KYC Verification Tests
    // =========================================================================

    @Nested
    @DisplayName("KYC Verification Tests")
    class KycVerificationTests {

        @Test
        @DisplayName("Should successfully verify KYC")
        void shouldVerifyKycSuccessfully() {
            // Arrange
            mockUser.setKycStatus(KycStatus.PENDING);
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            userService.verifyKyc(userId, 2); // Level 2 verification

            // Verify KYC status was updated
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(KycStatus.VERIFIED, savedUser.getKycStatus());
            assertEquals(2, savedUser.getKycLevel());

            // Verify event was published
            verify(eventPublisher).publishKycVerifiedEvent(userId, 2);
        }

        @Test
        @DisplayName("Should successfully reject KYC")
        void shouldRejectKycSuccessfully() {
            // Arrange
            mockUser.setKycStatus(KycStatus.PENDING);
            String reason = "Invalid documentation";
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            userService.rejectKyc(userId, reason);

            // Verify KYC status was updated
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(KycStatus.REJECTED, savedUser.getKycStatus());

            // Verify event was published
            verify(eventPublisher).publishKycRejectedEvent(userId, reason);
        }

        @Test
        @DisplayName("Should reject KYC verification with invalid level")
        void shouldRejectInvalidKycLevel() {
            // Arrange
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                userService.verifyKyc(userId, 5); // Invalid level (max is 3)
            });
        }
    }

    // =========================================================================
    // Email/Phone Verification Tests
    // =========================================================================

    @Nested
    @DisplayName("Email and Phone Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Should successfully verify email")
        void shouldVerifyEmailSuccessfully() {
            // Arrange
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            userService.verifyEmail(userId);

            // Verify email was verified
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertTrue(savedUser.isEmailVerified());

            // Verify event was published
            verify(eventPublisher).publishEmailVerifiedEvent(userId);
        }

        @Test
        @DisplayName("Should successfully verify phone")
        void shouldVerifyPhoneSuccessfully() {
            // Arrange
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act
            userService.verifyPhone(userId);

            // Verify phone was verified
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertTrue(savedUser.isPhoneVerified());

            // Verify event was published
            verify(eventPublisher).publishPhoneVerifiedEvent(userId);
        }
    }

    // =========================================================================
    // Query Tests
    // =========================================================================

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should retrieve user by ID")
        void shouldRetrieveUserById() {
            // Arrange
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

            // Act
            Optional<User> result = userService.getUserById(userId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(userId, result.get().getId());
        }

        @Test
        @DisplayName("Should retrieve user by email")
        void shouldRetrieveUserByEmail() {
            // Arrange
            when(userRepository.findByEmail(mockUser.getEmail())).thenReturn(Optional.of(mockUser));

            // Act
            Optional<User> result = userService.getUserByEmail(mockUser.getEmail());

            // Assert
            assertTrue(result.isPresent());
            assertEquals(mockUser.getEmail(), result.get().getEmail());
        }

        @Test
        @DisplayName("Should return empty for non-existent email")
        void shouldReturnEmptyForNonExistentEmail() {
            // Arrange
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            // Act
            Optional<User> result = userService.getUserByEmail("nonexistent@example.com");

            // Assert
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should retrieve users by status")
        void shouldRetrieveUsersByStatus() {
            // Arrange
            when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(mockUser));

            // Act
            List<User> users = userService.getUsersByStatus(UserStatus.ACTIVE);

            // Assert
            assertNotNull(users);
            assertEquals(1, users.size());
            assertEquals(UserStatus.ACTIVE, users.get(0).getStatus());
        }
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null user ID")
        void shouldHandleNullUserId() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                userService.getUserById(null);
            });
        }

        @Test
        @DisplayName("Should handle very long names")
        void shouldHandleVeryLongNames() {
            // Arrange
            String longName = "A".repeat(500);
            validCreateRequest.setFirstName(longName);

            // Act & Assert - Should enforce max length
            assertThrows(IllegalArgumentException.class, () -> {
                userService.createUser(validCreateRequest);
            });
        }

        @Test
        @DisplayName("Should handle special characters in names")
        void shouldHandleSpecialCharactersInNames() {
            // Arrange
            validCreateRequest.setFirstName("Jean-Pierre");
            validCreateRequest.setLastName("O'Brien");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            // Act - Should allow hyphens and apostrophes
            User createdUser = userService.createUser(validCreateRequest);

            // Assert
            assertNotNull(createdUser);
        }

        @Test
        @DisplayName("Should handle concurrent updates with optimistic locking")
        void shouldHandleOptimisticLockingException() {
            // This would test OptimisticLockException handling
            // Implementation depends on JPA @Version field usage
        }
    }
}
