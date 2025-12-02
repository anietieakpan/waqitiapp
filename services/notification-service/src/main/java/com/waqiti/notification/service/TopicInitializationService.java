package com.waqiti.notification.service;

import com.waqiti.notification.dto.CreateTopicRequest;
import com.waqiti.notification.repository.NotificationTopicRepository;
import com.waqiti.notification.service.TopicManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicInitializationService implements ApplicationRunner {
    
    private final TopicManagementService topicManagementService;
    private final NotificationTopicRepository topicRepository;
    
    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing default notification topics...");
        initializeDefaultTopics();
        log.info("Default notification topics initialization completed");
    }
    
    private void initializeDefaultTopics() {
        List<CreateTopicRequest> defaultTopics = Arrays.asList(
            // Payment Topics
            CreateTopicRequest.builder()
                .name("payment_received")
                .displayName("Payment Received")
                .description("Notifications when you receive a payment")
                .category("PAYMENT")
                .active(true)
                .autoSubscribe(true)
                .priority(10)
                .icon("payment")
                .color("#4CAF50")
                .build(),
                
            CreateTopicRequest.builder()
                .name("payment_sent")
                .displayName("Payment Sent")
                .description("Notifications when you send a payment")
                .category("PAYMENT")
                .active(true)
                .autoSubscribe(true)
                .priority(9)
                .icon("send")
                .color("#2196F3")
                .build(),
                
            CreateTopicRequest.builder()
                .name("payment_failed")
                .displayName("Payment Failed")
                .description("Notifications when a payment fails")
                .category("PAYMENT")
                .active(true)
                .autoSubscribe(true)
                .priority(8)
                .icon("error")
                .color("#F44336")
                .build(),
                
            CreateTopicRequest.builder()
                .name("cashback_earned")
                .displayName("Cashback Earned")
                .description("Notifications when you earn cashback")
                .category("PAYMENT")
                .active(true)
                .autoSubscribe(true)
                .priority(7)
                .icon("attach_money")
                .color("#FF9800")
                .build(),
                
            // Security Topics
            CreateTopicRequest.builder()
                .name("login_alert")
                .displayName("Login Alerts")
                .description("Notifications for new device logins")
                .category("SECURITY")
                .active(true)
                .autoSubscribe(true)
                .priority(10)
                .icon("security")
                .color("#F44336")
                .build(),
                
            CreateTopicRequest.builder()
                .name("password_changed")
                .displayName("Password Changed")
                .description("Notifications when password is changed")
                .category("SECURITY")
                .active(true)
                .autoSubscribe(true)
                .priority(9)
                .icon("lock")
                .color("#F44336")
                .build(),
                
            CreateTopicRequest.builder()
                .name("suspicious_activity")
                .displayName("Suspicious Activity")
                .description("Notifications for suspicious account activity")
                .category("SECURITY")
                .active(true)
                .autoSubscribe(true)
                .priority(8)
                .icon("warning")
                .color("#FF5722")
                .build(),
                
            // Social Topics
            CreateTopicRequest.builder()
                .name("friend_request")
                .displayName("Friend Requests")
                .description("Notifications for new friend requests")
                .category("SOCIAL")
                .active(true)
                .autoSubscribe(false)
                .priority(6)
                .icon("person_add")
                .color("#2196F3")
                .build(),
                
            CreateTopicRequest.builder()
                .name("message_received")
                .displayName("Messages")
                .description("Notifications for new messages")
                .category("SOCIAL")
                .active(true)
                .autoSubscribe(false)
                .priority(5)
                .icon("message")
                .color("#2196F3")
                .build(),
                
            CreateTopicRequest.builder()
                .name("split_request")
                .displayName("Split Requests")
                .description("Notifications for bill split requests")
                .category("SOCIAL")
                .active(true)
                .autoSubscribe(true)
                .priority(7)
                .icon("call_split")
                .color("#4CAF50")
                .build(),
                
            // Promotional Topics
            CreateTopicRequest.builder()
                .name("special_offers")
                .displayName("Special Offers")
                .description("Notifications for special offers and deals")
                .category("PROMOTIONAL")
                .active(true)
                .autoSubscribe(false)
                .priority(3)
                .icon("local_offer")
                .color("#FF9800")
                .build(),
                
            CreateTopicRequest.builder()
                .name("rewards_update")
                .displayName("Rewards Updates")
                .description("Notifications about your rewards and points")
                .category("PROMOTIONAL")
                .active(true)
                .autoSubscribe(true)
                .priority(4)
                .icon("stars")
                .color("#FFC107")
                .build(),
                
            CreateTopicRequest.builder()
                .name("new_features")
                .displayName("New Features")
                .description("Notifications about new app features")
                .category("PROMOTIONAL")
                .active(true)
                .autoSubscribe(false)
                .priority(2)
                .icon("new_releases")
                .color("#9C27B0")
                .build(),
                
            // System Topics
            CreateTopicRequest.builder()
                .name("app_updates")
                .displayName("App Updates")
                .description("Notifications about app updates")
                .category("SYSTEM")
                .active(true)
                .autoSubscribe(false)
                .priority(1)
                .icon("system_update")
                .color("#9E9E9E")
                .build(),
                
            CreateTopicRequest.builder()
                .name("maintenance")
                .displayName("Maintenance Notices")
                .description("Notifications about scheduled maintenance")
                .category("SYSTEM")
                .active(true)
                .autoSubscribe(true)
                .priority(5)
                .icon("build")
                .color("#795548")
                .build(),
                
            CreateTopicRequest.builder()
                .name("account_verification")
                .displayName("Account Verification")
                .description("Notifications about account verification steps")
                .category("SYSTEM")
                .active(true)
                .autoSubscribe(true)
                .priority(8)
                .icon("verified_user")
                .color("#3F51B5")
                .build(),
                
            // Platform-specific topics
            CreateTopicRequest.builder()
                .name("all_users")
                .displayName("All Users")
                .description("General notifications for all users")
                .category("SYSTEM")
                .active(true)
                .autoSubscribe(true)
                .priority(1)
                .icon("people")
                .color("#607D8B")
                .build(),
                
            CreateTopicRequest.builder()
                .name("platform_ios")
                .displayName("iOS Users")
                .description("Notifications specific to iOS users")
                .category("SYSTEM")
                .active(true)
                .autoSubscribe(false)
                .priority(0)
                .icon("phone_iphone")
                .color("#000000")
                .build(),
                
            CreateTopicRequest.builder()
                .name("platform_android")
                .displayName("Android Users")
                .description("Notifications specific to Android users")
                .category("SYSTEM")
                .active(true)
                .autoSubscribe(false)
                .priority(0)
                .icon("phone_android")
                .color("#4CAF50")
                .build()
        );
        
        for (CreateTopicRequest topicRequest : defaultTopics) {
            try {
                if (!topicRepository.existsByName(topicRequest.getName())) {
                    topicManagementService.createTopic(topicRequest);
                    log.info("Created default topic: {}", topicRequest.getName());
                } else {
                    log.debug("Topic already exists: {}", topicRequest.getName());
                }
            } catch (Exception e) {
                log.error("Failed to create default topic: {}", topicRequest.getName(), e);
            }
        }
    }
}