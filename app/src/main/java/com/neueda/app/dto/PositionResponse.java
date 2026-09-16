package com.neueda.app.dto;

import lombok.Data;

@Data
public class PositionResponse {

    private Long positionId;
    private Long accountId; 
    private String symbol; 
    private int quantity;
    private double price;

}
