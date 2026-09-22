package com.neueda.app.dtos;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.models.Instrument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
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
