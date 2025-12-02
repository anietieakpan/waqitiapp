package com.waqiti.discovery.dto;

import com.waqiti.discovery.domain.DependencyType;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Register Dependencies Request
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDependenciesRequest {
    @NotEmpty(message = "Dependencies list cannot be empty")
    private List<String> dependencies;
    private DependencyType type;
    private Set<String> criticalDependencies;
}
