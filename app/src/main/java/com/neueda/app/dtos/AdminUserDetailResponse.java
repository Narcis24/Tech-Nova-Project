package com.neueda.app.dtos;

import com.neueda.app.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    private String email;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Role> roles;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
    private String createdBy;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AdminAccountResponse> accounts;
}