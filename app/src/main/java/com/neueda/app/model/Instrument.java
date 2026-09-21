package com.neueda.app.model;
import com.neueda.app.enums.AssetClass;

public record Instrument(
    String symbol, 
    String name, 
    AssetClass assetClass, 
    String currency, 
    boolean tradable
    ) {

    public boolean isTradable() {
        return tradable;
    }
}
