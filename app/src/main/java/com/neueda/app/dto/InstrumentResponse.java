package com.neueda.app.dto;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.model.Instrument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentResponse {
    private String symbol;
    private String name;
    private AssetClass assetClass;
    private String currency;
    private boolean tradable;
    
    public InstrumentResponse(Instrument instrument) {
        this.symbol = instrument.symbol();
        this.name = instrument.name();
        this.assetClass = instrument.assetClass();
        this.currency = instrument.currency();
        this.tradable = instrument.tradable();
    }
}
