package com.waqiti.user.service;

import com.waqiti.common.batch.BatchProcessingService;
import com.waqiti.user.domain.User;
import com.waqiti.user.domain.UserStatus;
import com.waqiti.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Cached user service with optimized database access patterns
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CachedUserService {

    @Lazy
    private final CachedUserService self;
    private final UserRepository userRepository;
    private final BatchProcessingService batchProcessingService;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void initializeBatchProcessors() {
        // Register batch processor for user updates
        batchProcessingService.registerBatchProcessor("userUpdates", 
            BatchProcessingService.BatchConfiguration.builder()
                .batchSize(50)
                .maxWaitTime(Duration.ofSeconds(30))
                .maxRetries(3)
                .processor(this::processBatchUserUpdates)
                .build());

        // Register batch processor for user activity tracking
        batchProcessingService.registerBatchProcessor("userActivity",
            BatchProcessingService.BatchConfiguration.builder()
                .batchSize(100)
                .maxWaitTime(Duration.ofSeconds(10))
                .maxRetries(2)
                .processor(this::processBatchUserActivity)
                .build());
    }

    /**
     * Get user by ID with L1 and L2 caching
     */
    @Cacheable(value = "users", key = "#userId", cacheManager = "l2CacheManager")
    public Optional<User> findById(UUID userId) {
        log.debug("Cache miss - fetching user from database: {}", userId);
        return userRepository.findById(userId);
    }

    /**
     * Get user by email with caching
     */
    @Cacheable(value = "users", key = "'email:' + #email", cacheManager = "l2CacheManager")
    public Optional<User> findByEmail(String email) {
        log.debug("Cache miss - fetching user by email from database: {}", email);
        return userRepository.findByEmail(email);
    }

    /**
     * Get user by username with caching
     */
    @Cacheable(value = "users", key = "'username:' + #username", cacheManager = "l2CacheManager")
    public Optional<User> findByUsername(String username) {
        log.debug("Cache miss - fetching user by username from database: {}", username);
        return userRepository.findByUsername(username);
    }

    /**
     * Search users with optimized full-text search and caching
     */
    @Cacheable(value = "userSearch", key = "#searchTerm", cacheManager = "l1CacheManager")
    public List<User> searchUsers(String searchTerm) {
        log.debug("Cache miss - performing user search: {}", searchTerm);
        
        // Try full-text search first
        List<User> results = userRepository.findByUsernameOrEmailContaining(searchTerm);
        
        // Fallback to trigram search if no results
        if (results.isEmpty()) {
            results = userRepository.findByTrigramSimilarity(searchTerm);
        }
        
        return results;
    }

    /**
     * Update user with cache invalidation
     */
    @CachePut(value = "users", key = "#user.id", cacheManager = "l2CacheManager")
    @CacheEvict(value = "users", 
                key = "'email:' + #user.email + ', username:' + #user.username", 
                cacheManager = "l2CacheManager")
    @Transactional
    public User updateUser(User user) {
        log.debug("Updating user and refreshing cache: {}", user.getId());
        return userRepository.save(user);
    }

    /**
     * Batch update user last activity - queued for performance
     */
    public void updateUserActivity(UUID userId) {
        batchProcessingService.addToBatch("userActivity", 
            UserActivityUpdate.builder()
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * Batch process user updates
     */
    private void processBatchUserUpdates(List<Object> updates) {
        log.info("Processing batch of {} user updates", updates.size());
        
        List<User> users = updates.stream()
            .map(User.class::cast)
            .toList();
            
        try {
            userRepository.saveAll(users);
            
            // Invalidate cache for updated users
            users.forEach(user -> {
                self.evictUserFromCache(user.getId());
            });
            
        } catch (Exception e) {
            log.error("Failed to process batch user updates", e);
            throw e;
        }
    }

    /**
     * Batch process user activity updates
     */
    private void processBatchUserActivity(List<Object> activities) {
        log.info("Processing batch of {} user activity updates", activities.size());
        
        List<UserActivityUpdate> activityUpdates = activities.stream()
            .map(UserActivityUpdate.class::cast)
            .toList();

        try {
            // Batch update user last activity timestamps
            List<UUID> userIds = activityUpdates.stream()
                .map(UserActivityUpdate::getUserId)
                .distinct()
                .toList();
                
            userRepository.batchUpdateLastActivity(userIds, LocalDateTime.now());
            
            // Invalidate cache for updated users
            userIds.forEach(self::evictUserFromCache);
            
        } catch (Exception e) {
            log.error("Failed to process batch user activity updates", e);
            throw e;
        }
    }

    /**
     * Get user statistics with caching
     */
    @Cacheable(value = "userStats", key = "#status", cacheManager = "l2CacheManager")
    public UserStatistics getUserStatistics(UserStatus status) {
        log.debug("Cache miss - calculating user statistics for status: {}", status);
        
        long totalUsers = userRepository.countByStatus(status);
        long activeToday = userRepository.countActiveUsersToday(LocalDateTime.now().toLocalDate().atStartOfDay());
        long newUsersThisWeek = userRepository.countNewUsersThisWeek(LocalDateTime.now().minusDays(7));
        
        return UserStatistics.builder()
            .totalUsers(totalUsers)
            .activeToday(activeToday)
            .newUsersThisWeek(newUsersThisWeek)
            .calculatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Evict user from all cache layers
     */
    @Caching(evict = {
        @CacheEvict(value = {"users", "userStats"}, key = "#userId", cacheManager = "l2CacheManager"),
        @CacheEvict(value = {"users", "userStats"}, key = "#userId", cacheManager = "l1CacheManager")
    })
    public void evictUserFromCache(UUID userId) {
        log.debug("Evicting user from cache: {}", userId);
    }

    /**
     * Check if user exists with caching
     */
    @Cacheable(value = "userExists", key = "#email", cacheManager = "l1CacheManager")
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Check if username exists with caching
     */
    @Cacheable(value = "userExists", key = "#username", cacheManager = "l1CacheManager")
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Preload frequently accessed users into cache
     */
    @Transactional(readOnly = true)
    public void preloadFrequentUsers() {
        log.info("Preloading frequent users into cache");
        
        List<User> frequentUsers = userRepository.findMostActiveUsers(PageRequest.of(0, 100));
        
        // Get the Spring proxy to ensure cache annotations work
        CachedUserService self = applicationContext.getBean(CachedUserService.class);
        
        frequentUsers.forEach(user -> {
            // Use proxy to ensure cache annotations are processed
            self.findById(user.getId());
        });
        
        log.info("Preloaded {} users into cache", frequentUsers.size());
    }
}