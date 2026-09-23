package com.neueda.app.dto;

import com.neueda.app.enums.AssetClass;
import com.neueda.app.model.Instrument;
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
        this.symbol = instrument.getSymbol();
        this.name = instrument.getName();
        this.assetClass = instrument.getAssetClass();
        this.currency = instrument.getCurrency();
        this.tradable = instrument.isTradable();
    }
}
