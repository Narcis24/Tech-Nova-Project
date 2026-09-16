package com.neueda.app.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class PositionResponse {

    private String accountID; 
    private String symbol; 
    private int quantity;
    private BigDecimal price;

}
