package com.neueda.app.model;
import com.neueda.app.enums.AssetClass;

public record Instrument(
    String symbol, 
    String name, 
    AssetClass assetClass, 
    String currency, 
    boolean tradable
    ) {

        public Instrument {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException("Symbol cannot be null");
            }

            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Symbol Name cannot be null");
            }

            if (assetClass == null) {
                throw new IllegalArgumentException("Asset Class cannot be null");
            }

            if (currency == null || currency.isBlank()) {
                throw new IllegalArgumentException("Currency cannot be null");
            }
        }

    public boolean isTradable() {
        return tradable;
    }
}
