package com.waqiti.common.model.incident;

import java.time.Duration;

public enum IncidentPriority {
    P0(Duration.ofMinutes(15), Duration.ofHours(4)),   // Critical - 15min ACK, 4hr resolve
    P1(Duration.ofMinutes(30), Duration.ofHours(24)),  // High - 30min ACK, 24hr resolve
    P2(Duration.ofHours(2), Duration.ofDays(3)),       // Medium - 2hr ACK, 3 days resolve
    P3(Duration.ofHours(8), Duration.ofDays(7)),       // Low - 8hr ACK, 7 days resolve
    P4(Duration.ofDays(1), Duration.ofDays(14));       // Minimal - 1 day ACK, 14 days resolve

    private final Duration acknowledgeSla;
    private final Duration resolveSla;

    IncidentPriority(Duration acknowledgeSla, Duration resolveSla) {
        this.acknowledgeSla = acknowledgeSla;
        this.resolveSla = resolveSla;
    }

    public Duration getAcknowledgeSla() {
        return acknowledgeSla;
    }

    public Duration getResolveSla() {
        return resolveSla;
    }

    public boolean isCritical() {
        return this == P0 || this == P1;
    }
}
