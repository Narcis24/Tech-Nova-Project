package com.neueda.app.dtos;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRejectedEvent {
    private UUID orderId;
    private String accountId;
    private String symbol;
    private String reason;
}
