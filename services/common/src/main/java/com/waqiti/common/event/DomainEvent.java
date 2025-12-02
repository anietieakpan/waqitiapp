// File: common/src/main/java/com/waqiti/common/event/DomainEvent.java
package com.waqiti.common.event;

import java.time.LocalDateTime;

public interface DomainEvent {
    String getEventId();
    String getEventType();
    LocalDateTime getTimestamp();
    String getTopic();
}