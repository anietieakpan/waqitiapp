// File: services/user-service/src/main/java/com/waqiti/user/dto/MfaStatusResponse.java
package com.waqiti.user.dto;

import com.waqiti.user.domain.MfaMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaStatusResponse {
    private boolean enabled;
    private List<MfaMethod> methods;
}