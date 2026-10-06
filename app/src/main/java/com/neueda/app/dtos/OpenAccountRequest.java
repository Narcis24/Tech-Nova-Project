package com.neueda.app.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenAccountRequest {
    @NotBlank(message = "Holder name is required")
    @Size(max = 255, message = "Holder name cannot exceed 255 characters")
    private String holderName;
}
