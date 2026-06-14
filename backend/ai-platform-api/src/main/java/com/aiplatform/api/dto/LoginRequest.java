package com.aiplatform.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Login request")
public class LoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private Long tenantId;
}
