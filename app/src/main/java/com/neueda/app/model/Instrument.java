package com.neueda.app.model;

import com.neueda.app.enums.AssetClass;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "instruments")
public class Instrument {
    @Id
    private String symbol;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "asset_class")
    @Enumerated(EnumType.STRING)
    private AssetClass assetClass;
    
    @Column(name = "currency")
    private String currency;
    
    @Column(name = "tradable")
    private boolean tradable;

    public Instrument() {
    }

    public Instrument(String symbol, String name, AssetClass assetClass, String currency, boolean tradable) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Instrument Name cannot be null");
        }

        if (assetClass == null) {
            throw new IllegalArgumentException("Asset Class cannot be null");
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        
        this.symbol = symbol;
        this.name = name;
        this.assetClass = assetClass;
        this.currency = currency;
        this.tradable = tradable;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isTradable() {
        return tradable;
    }
}
