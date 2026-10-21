package com.neueda.app.dtos;

import com.neueda.app.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateAccountStatusRequest {
    @NotNull(message = "status is required")
    private AccountStatus status;
}