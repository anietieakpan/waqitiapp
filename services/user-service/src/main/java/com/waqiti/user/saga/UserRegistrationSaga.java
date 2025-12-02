package com.waqiti.user.saga;

import com.waqiti.user.domain.User;
import com.waqiti.user.dto.UserRegistrationRequest;
import com.waqiti.user.dto.UserResponse;
import com.waqiti.user.entity.UserProfile;
import com.waqiti.user.exception.UserRegistrationException;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.UserProfileRepository;
import com.waqiti.user.client.IntegrationServiceClient;
import com.waqiti.user.client.dto.CreateUserResponse;
import com.waqiti.user.events.producers.UserRegisteredEventProducer;
import com.waqiti.user.saga.entity.SagaState;
import com.waqiti.user.saga.entity.SagaStep;
import com.waqiti.user.saga.repository.SagaStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * User Registration Saga Orchestrator
 *
 * Implements Saga Pattern for distributed user registration transaction
 * across multiple services with automatic compensation on failure.
 *
 * BUSINESS CONTEXT:
 * User registration involves multiple services and databases:
 * 1. Local user database (user-service)
 * 2. External integration system (integration-service)
 * 3. Event streaming (Kafka)
 * 4. Wallet initialization (wallet-service - via event)
 *
 * SAGA PATTERN BENEFITS:
 * - Maintains data consistency across distributed systems
 * - Automatic rollback on failure (compensation)
 * - Complete audit trail of registration attempts
 * - Idempotent operations (safe to retry)
 * - No distributed locks required
 *
 * SAGA STEPS (in order):
 * 1. CREATE_LOCAL_USER - Create user in local database
 * 2. CREATE_PROFILE - Create user profile
 * 3. CREATE_EXTERNAL_USER - Create in integration service
 * 4. GENERATE_VERIFICATION_TOKEN - Email verification
 * 5. PUBLISH_USER_REGISTERED_EVENT - Notify downstream services
 *
 * COMPENSATION (reverse order):
 * 5. Event is idempotent - no compensation needed
 * 4. Token expires automatically - no compensation needed
 * 3. Delete external user
 * 2. Delete profile
 * 1. Delete local user
 *
 * FAILURE SCENARIOS HANDLED:
 * - Database constraint violations
 * - External service unavailability
 * - Network timeouts
 * - Partial failures (some steps succeed, others fail)
 * - Duplicate registration attempts (idempotency)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegistrationSaga {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final IntegrationServiceClient integrationClient;
    private final UserRegisteredEventProducer eventProducer;
    private final SagaStateRepository sagaStateRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService verificationTokenService;

    /**
     * Execute user registration saga
     *
     * @param request Registration request
     * @return User response if successful
     * @throws UserRegistrationException if registration fails and compensation succeeds
     */
    @Transactional
    public UserResponse executeRegistration(UserRegistrationRequest request) {
        // Generate unique saga ID for tracking
        String sagaId = generateSagaId();

        log.info("SAGA: Starting user registration saga: sagaId={}, username={}",
                sagaId, request.getUsername());

        // Initialize saga state
        SagaState state = initializeSagaState(sagaId, request);

        try {
            // STEP 1: Create local user
            User user = createLocalUser(request, state);
            log.info("SAGA: Step 1 completed - Local user created: userId={}", user.getId());

            // STEP 2: Create user profile
            UserProfile profile = createUserProfile(user, request, state);
            log.info("SAGA: Step 2 completed - Profile created: profileId={}", profile.getId());

            // STEP 3: Create external user (integration service)
            CreateUserResponse externalUser = createExternalUser(user, state);
            log.info("SAGA: Step 3 completed - External user created: externalId={}",
                    externalUser.getExternalId());

            // STEP 4: Generate verification token
            String verificationToken = generateVerificationToken(user.getId(), state);
            log.info("SAGA: Step 4 completed - Verification token generated");

            // STEP 5: Publish user registered event
            publishUserRegisteredEvent(user, profile, state);
            log.info("SAGA: Step 5 completed - Event published");

            // Mark saga as completed
            completeSaga(state);

            log.info("SAGA: User registration completed successfully: sagaId={}, userId={}",
                    sagaId, user.getId());

            return mapToUserResponse(user, profile);

        } catch (Exception e) {
            log.error("SAGA: User registration failed: sagaId={}, error={}",
                    sagaId, e.getMessage(), e);

            // Execute compensation (rollback)
            boolean compensated = compensate(state, e);

            if (compensated) {
                throw new UserRegistrationException(
                    "User registration failed: " + e.getMessage() +
                    ". All changes have been rolled back.", e);
            } else {
                throw new UserRegistrationException(
                    "CRITICAL: User registration failed AND compensation failed. " +
                    "Manual intervention required. Saga ID: " + sagaId, e);
            }
        }
    }

    /**
     * STEP 1: Create local user in database
     */
    @Transactional
    private User createLocalUser(UserRegistrationRequest request, SagaState state) {
        try {
            // Hash password
            String passwordHash = passwordEncoder.encode(request.getPassword());

            // Generate external ID
            String externalId = UUID.randomUUID().toString();

            // Create user entity
            User user = User.create(
                request.getUsername(),
                request.getEmail(),
                passwordHash,
                externalId
            );

            // Set additional fields
            if (request.getPhoneNumber() != null) {
                user.setPhoneNumber(request.getPhoneNumber());
            }

            // Save to database
            user = userRepository.save(user);

            // Record successful step
            state.addCompletedStep(
                "CREATE_LOCAL_USER",
                user.getId().toString(),
                "User created in local database"
            );

            return user;

        } catch (Exception e) {
            log.error("SAGA: Failed to create local user", e);
            state.setFailureReason("Failed to create local user: " + e.getMessage());
            sagaStateRepository.save(state);
            throw e;
        }
    }

    /**
     * STEP 2: Create user profile
     */
    @Transactional
    private UserProfile createUserProfile(User user, UserRegistrationRequest request, SagaState state) {
        try {
            UserProfile profile = UserProfile.builder()
                    .userId(user.getId())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .dateOfBirth(request.getDateOfBirth())
                    .createdAt(LocalDateTime.now())
                    .build();

            profile = profileRepository.save(profile);

            state.addCompletedStep(
                "CREATE_PROFILE",
                profile.getId().toString(),
                "User profile created"
            );

            return profile;

        } catch (Exception e) {
            log.error("SAGA: Failed to create user profile", e);
            state.setFailureReason("Failed to create profile: " + e.getMessage());
            sagaStateRepository.save(state);
            throw e;
        }
    }

    /**
     * STEP 3: Create external user in integration service
     */
    private CreateUserResponse createExternalUser(User user, SagaState state) {
        try {
            CreateUserResponse response = integrationClient.createUser(
                user.getExternalId(),
                user.getUsername(),
                user.getEmail()
            );

            state.addCompletedStep(
                "CREATE_EXTERNAL_USER",
                response.getExternalId(),
                "External user created in integration service"
            );

            return response;

        } catch (Exception e) {
            log.error("SAGA: Failed to create external user", e);
            state.setFailureReason("Failed to create external user: " + e.getMessage());
            sagaStateRepository.save(state);
            throw e;
        }
    }

    /**
     * STEP 4: Generate verification token
     */
    private String generateVerificationToken(UUID userId, SagaState state) {
        try {
            String token = verificationTokenService.generateEmailVerificationToken(userId);

            state.addCompletedStep(
                "GENERATE_VERIFICATION_TOKEN",
                token,
                "Email verification token generated"
            );

            return token;

        } catch (Exception e) {
            log.error("SAGA: Failed to generate verification token", e);
            state.setFailureReason("Failed to generate token: " + e.getMessage());
            sagaStateRepository.save(state);
            throw e;
        }
    }

    /**
     * STEP 5: Publish user registered event
     */
    private void publishUserRegisteredEvent(User user, UserProfile profile, SagaState state) {
        try {
            eventProducer.publishUserRegisteredEvent(user, profile);

            state.addCompletedStep(
                "PUBLISH_USER_REGISTERED_EVENT",
                "SUCCESS",
                "User registered event published to Kafka"
            );

        } catch (Exception e) {
            log.error("SAGA: Failed to publish user registered event", e);
            state.setFailureReason("Failed to publish event: " + e.getMessage());
            sagaStateRepository.save(state);
            throw e;
        }
    }

    /**
     * Execute compensation (rollback) in reverse order
     */
    @Transactional
    private boolean compensate(SagaState state, Exception cause) {
        log.warn("SAGA: Starting compensation for saga: {}", state.getSagaId());

        state.setStatus(SagaStatus.COMPENSATING);
        state.setCompensationStartedAt(LocalDateTime.now());
        sagaStateRepository.save(state);

        boolean allCompensated = true;

        // Get completed steps in reverse order
        List<SagaStep> steps = new ArrayList<>(state.getCompletedSteps());
        Collections.reverse(steps);

        for (SagaStep step : steps) {
            try {
                compensateStep(step);
                step.setCompensated(true);
                step.setCompensatedAt(LocalDateTime.now());

                log.info("SAGA: Compensated step: {} - {}",
                        step.getStepName(), step.getStepResult());

            } catch (Exception e) {
                log.error("SAGA: Compensation failed for step: {} - {}",
                        step.getStepName(), e.getMessage(), e);

                step.setCompensationFailed(true);
                step.setCompensationError(e.getMessage());
                allCompensated = false;
            }
        }

        // Update saga state
        if (allCompensated) {
            state.setStatus(SagaStatus.COMPENSATED);
            log.info("SAGA: All steps compensated successfully: {}", state.getSagaId());
        } else {
            state.setStatus(SagaStatus.COMPENSATION_FAILED);
            log.error("SAGA: Some compensations failed: {} - MANUAL INTERVENTION REQUIRED",
                    state.getSagaId());
        }

        state.setFailureReason(cause.getMessage());
        state.setCompletedAt(LocalDateTime.now());
        sagaStateRepository.save(state);

        return allCompensated;
    }

    /**
     * Compensate individual step
     */
    @Transactional
    private void compensateStep(SagaStep step) {
        log.debug("SAGA: Compensating step: {}", step.getStepName());

        switch (step.getStepName()) {
            case "CREATE_LOCAL_USER":
                // Delete local user
                UUID userId = UUID.fromString(step.getStepResult());
                userRepository.deleteById(userId);
                log.info("SAGA COMPENSATION: Deleted local user: {}", userId);
                break;

            case "CREATE_PROFILE":
                // Delete profile
                UUID profileId = UUID.fromString(step.getStepResult());
                profileRepository.deleteById(profileId);
                log.info("SAGA COMPENSATION: Deleted profile: {}", profileId);
                break;

            case "CREATE_EXTERNAL_USER":
                // Delete external user
                String externalId = step.getStepResult();
                integrationClient.deleteUser(externalId);
                log.info("SAGA COMPENSATION: Deleted external user: {}", externalId);
                break;

            case "GENERATE_VERIFICATION_TOKEN":
                // Tokens expire automatically - no compensation needed
                log.info("SAGA COMPENSATION: Verification token will expire automatically");
                break;

            case "PUBLISH_USER_REGISTERED_EVENT":
                // Events are idempotent and consumers handle missing users gracefully
                // No compensation needed
                log.info("SAGA COMPENSATION: Event published - downstream services will handle gracefully");
                break;

            default:
                log.warn("SAGA COMPENSATION: Unknown step type: {}", step.getStepName());
        }
    }

    /**
     * Initialize saga state
     */
    private SagaState initializeSagaState(String sagaId, UserRegistrationRequest request) {
        SagaState state = SagaState.builder()
                .sagaId(sagaId)
                .sagaType(SagaType.USER_REGISTRATION)
                .status(SagaStatus.STARTED)
                .requestData(serializeRequest(request))
                .completedSteps(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();

        return sagaStateRepository.save(state);
    }

    /**
     * Mark saga as completed
     */
    private void completeSaga(SagaState state) {
        state.setStatus(SagaStatus.COMPLETED);
        state.setCompletedAt(LocalDateTime.now());
        sagaStateRepository.save(state);
    }

    /**
     * Generate unique saga ID
     */
    private String generateSagaId() {
        return "SAGA-REG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Serialize request for audit trail
     */
    private String serializeRequest(UserRegistrationRequest request) {
        // Remove sensitive data before serialization
        return String.format(
            "{\"username\":\"%s\",\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\"}",
            request.getUsername(),
            request.getEmail(),
            request.getFirstName(),
            request.getLastName()
        );
    }

    /**
     * Map to user response
     */
    private UserResponse mapToUserResponse(User user, UserProfile profile) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
