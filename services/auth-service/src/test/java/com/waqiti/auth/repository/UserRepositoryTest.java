package com.waqiti.auth.repository;

import com.waqiti.auth.domain.Role;
import com.waqiti.auth.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for UserRepository.
 *
 * Tests cover:
 * - Basic CRUD operations
 * - Custom query methods
 * - Security queries
 * - Pagination
 * - Soft delete
 * - Optimistic locking
 *
 * @author Waqiti Platform Team
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Integration Tests")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User testUser;
    private Role userRole;

    @BeforeEach
    void setUp() {
        // Create test role
        userRole = Role.builder()
            .name("USER")
            .displayName("Standard User")
            .roleType(Role.RoleType.USER)
            .priority(50)
            .isSystemRole(true)
            .isActive(true)
            .build();
        roleRepository.save(userRole);

        // Create test user
        testUser = User.builder()
            .username("testuser")
            .email("test@example.com")
            .phoneNumber("+1234567890")
            .passwordHash("$2a$10$hashed_password")
            .firstName("Test")
            .lastName("User")
            .accountStatus(User.AccountStatus.ACTIVE)
            .emailVerified(true)
            .phoneVerified(false)
            .twoFactorEnabled(false)
            .failedLoginAttempts(0)
            .locale("en_US")
            .timezone("UTC")
            .build();

        testUser.getRoles().add(userRole);
    }

    @Test
    @DisplayName("Should save user successfully")
    void shouldSaveUser() {
        // When
        User savedUser = userRepository.save(testUser);

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getUsername()).isEqualTo("testuser");
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
        assertThat(savedUser.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should find user by username")
    void shouldFindByUsername() {
        // Given
        userRepository.save(testUser);

        // When
        Optional<User> found = userRepository.findByUsernameAndDeletedFalse("testuser");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should find user by email")
    void shouldFindByEmail() {
        // Given
        userRepository.save(testUser);

        // When
        Optional<User> found = userRepository.findByEmailAndDeletedFalse("test@example.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should not find deleted user")
    void shouldNotFindDeletedUser() {
        // Given
        testUser.softDelete();
        userRepository.save(testUser);

        // When
        Optional<User> found = userRepository.findByUsernameAndDeletedFalse("testuser");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should check username exists")
    void shouldCheckUsernameExists() {
        // Given
        userRepository.save(testUser);

        // When
        boolean exists = userRepository.existsByUsernameAndDeletedFalse("testuser");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should find active users only")
    void shouldFindActiveUsers() {
        // Given
        User activeUser = userRepository.save(testUser);

        User inactiveUser = User.builder()
            .username("inactive")
            .email("inactive@example.com")
            .passwordHash("hash")
            .accountStatus(User.AccountStatus.SUSPENDED)
            .build();
        userRepository.save(inactiveUser);

        // When
        List<User> activeUsers = userRepository.findAllActiveUsers();

        // Then
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should increment failed login attempts")
    void shouldIncrementFailedLoginAttempts() {
        // Given
        User user = userRepository.save(testUser);

        // When
        user.incrementFailedLoginAttempts();
        userRepository.save(user);

        entityManager.flush();
        entityManager.clear();

        // Then
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should lock account after 5 failed attempts")
    void shouldLockAccountAfter5FailedAttempts() {
        // Given
        User user = userRepository.save(testUser);

        // When
        for (int i = 0; i < 5; i++) {
            user.incrementFailedLoginAttempts();
        }
        userRepository.save(user);

        entityManager.flush();
        entityManager.clear();

        // Then
        User locked = userRepository.findById(user.getId()).orElseThrow();
        assertThat(locked.getAccountStatus()).isEqualTo(User.AccountStatus.LOCKED);
        assertThat(locked.getAccountLockedUntil()).isNotNull();
        assertThat(locked.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("Should reset failed login attempts")
    void shouldResetFailedLoginAttempts() {
        // Given
        testUser.incrementFailedLoginAttempts();
        testUser.incrementFailedLoginAttempts();
        User user = userRepository.save(testUser);

        // When
        userRepository.resetFailedLoginAttempts(user.getId());

        entityManager.flush();
        entityManager.clear();

        // Then
        User reset = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reset.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(reset.getAccountLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Should update last login")
    void shouldUpdateLastLogin() {
        // Given
        User user = userRepository.save(testUser);

        // When
        user.updateLastLogin("192.168.1.1");
        userRepository.save(user);

        entityManager.flush();
        entityManager.clear();

        // Then
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getLastLoginAt()).isNotNull();
        assertThat(updated.getLastLoginIp()).isEqualTo("192.168.1.1");
        assertThat(updated.getFailedLoginAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should find users by role")
    void shouldFindUsersByRole() {
        // Given
        userRepository.save(testUser);

        // When
        List<User> usersWithRole = userRepository.findUsersByRole("USER");

        // Then
        assertThat(usersWithRole).hasSize(1);
        assertThat(usersWithRole.get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should search users by term")
    void shouldSearchUsers() {
        // Given
        userRepository.save(testUser);

        User anotherUser = User.builder()
            .username("another")
            .email("another@example.com")
            .passwordHash("hash")
            .firstName("Another")
            .lastName("User")
            .build();
        userRepository.save(anotherUser);

        // When
        Page<User> results = userRepository.searchUsers("test", PageRequest.of(0, 10));

        // Then
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should perform soft delete")
    void shouldSoftDelete() {
        // Given
        User user = userRepository.save(testUser);
        UUID userId = user.getId();

        // When
        userRepository.softDelete(userId, LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        // Then
        User deleted = userRepository.findById(userId).orElseThrow();
        assertThat(deleted.getDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();

        // Should not be found in active users
        Optional<User> notFound = userRepository.findByUsernameAndDeletedFalse("testuser");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("Should handle optimistic locking")
    void shouldHandleOptimisticLocking() {
        // Given
        User user = userRepository.save(testUser);
        entityManager.flush();
        entityManager.clear();

        // When - Load same user in two different transactions
        User user1 = userRepository.findById(user.getId()).orElseThrow();
        User user2 = userRepository.findById(user.getId()).orElseThrow();

        // Update first instance
        user1.setFirstName("Updated1");
        userRepository.save(user1);
        entityManager.flush();

        // Try to update second instance (should fail with optimistic lock)
        user2.setFirstName("Updated2");

        // Then
        assertThatThrownBy(() -> {
            userRepository.save(user2);
            entityManager.flush();
        }).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Should count active users")
    void shouldCountActiveUsers() {
        // Given
        userRepository.save(testUser);

        User deleted = User.builder()
            .username("deleted")
            .email("deleted@example.com")
            .passwordHash("hash")
            .deleted(true)
            .build();
        userRepository.save(deleted);

        // When
        Long count = userRepository.countActiveUsers();

        // Then
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should find users with expired passwords")
    void shouldFindUsersWithExpiredPasswords() {
        // Given
        testUser.setPasswordExpiresAt(LocalDateTime.now().minusDays(1));
        userRepository.save(testUser);

        // When
        List<User> expired = userRepository.findUsersWithExpiredPasswords(LocalDateTime.now());

        // Then
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should implement UserDetails correctly")
    void shouldImplementUserDetailsCorrectly() {
        // Given
        User user = userRepository.save(testUser);

        // Then - Test UserDetails methods
        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getPassword()).isEqualTo("$2a$10$hashed_password");
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getAuthorities()).isNotEmpty();
    }

    @Test
    @DisplayName("Should not enable unverified user")
    void shouldNotEnableUnverifiedUser() {
        // Given
        testUser.setEmailVerified(false);
        User user = userRepository.save(testUser);

        // Then
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should find inactive users")
    void shouldFindInactiveUsers() {
        // Given
        testUser.setLastLoginAt(LocalDateTime.now().minusDays(91));
        userRepository.save(testUser);

        // When
        List<User> inactive = userRepository.findInactiveUsers(
            LocalDateTime.now().minusDays(90)
        );

        // Then
        assertThat(inactive).hasSize(1);
    }

    @Test
    @DisplayName("Should update password and expiry")
    void shouldUpdatePasswordAndExpiry() {
        // Given
        User user = userRepository.save(testUser);

        // When
        user.updatePassword("$2a$10$new_hashed_password");
        userRepository.save(user);

        entityManager.flush();
        entityManager.clear();

        // Then
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getPasswordHash()).isEqualTo("$2a$10$new_hashed_password");
        assertThat(updated.getLastPasswordChangeAt()).isNotNull();
        assertThat(updated.getPasswordExpiresAt()).isNotNull();
        assertThat(updated.getPasswordExpiresAt())
            .isAfter(LocalDateTime.now().plusDays(89)); // 90 days policy
    }
}
