package com.neueda.app.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SavePieRequest {
    @NotBlank
    @Size(max = 60)
    private String name;

    @Size(max = 280)
    private String description;

    @NotNull
    @Size(max = 20)
    private List<@Valid PieSliceRequest> slices;
}
