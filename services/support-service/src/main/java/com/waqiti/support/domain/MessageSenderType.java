package com.waqiti.support.domain;

public enum MessageSenderType {
    CUSTOMER,       // Message from customer
    AGENT,          // Message from support agent
    SYSTEM,         // Automated system message
    ADMIN,          // Message from administrator
    BOT,            // Message from chatbot/AI assistant
    ESCALATION,     // Message from escalation team
    SUPERVISOR,     // Message from supervisor
    EXTERNAL        // Message from external system/integration
}