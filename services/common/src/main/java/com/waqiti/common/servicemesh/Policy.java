package com.waqiti.common.servicemesh;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Base class for service mesh policies
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class Policy {
    private String name;
    private String serviceName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}