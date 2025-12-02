package com.waqiti.common.security.awareness.config;

import java.util.Properties;



// ============================================================================
// Application Configuration
// ============================================================================



import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.Properties;

@Configuration
@EnableScheduling
@EnableTransactionManagement
public class SecurityAwarenessConfiguration {

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com"); // Configure based on environment
        mailSender.setPort(587);

        // Configure from environment variables in production
        mailSender.setUsername("noreply@example.com");
        mailSender.setPassword("${SMTP_PASSWORD}");

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "false");

        return mailSender;
    }
}
