package com.neueda.app.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A pie at its latest version. cashPct is the part of the pie not allocated to any slice. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PieResponse {
    private UUID pieId;
    private String name;
    private String description;
    private String visibility;
    private int version;
    private List<PieSliceResponse> slices;
    private BigDecimal cashPct;
    private List<String> linkedAccountIds;
    private LocalDateTime createdAt;
}
