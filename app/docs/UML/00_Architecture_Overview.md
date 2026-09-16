# Tech-Nova UML Diagrams

## High-level overview

To follow best development practices, instead of one complex UML diagram we've split the architecture into three focused diagrams:

### Diagram 1: Trading & Order Management
- **File**: `01_Trading_Order_Management.mmd`
- **Focus**: Order lifecycle (creation, validation, execution, cancellation)
- **Key Classes**: Account, Order, Instrument, OrderService
- **Key Enums**: OrderStatus (PENDING, FILLED, CANCELLED,  REJECTED, PARTIALLY_FILLED, EXPIRED), OrderSide (BUY, SELL), AccountStatus (ACTIVE, INACTIVE, SUSPENDED, PENDING, CLOSED)

### Diagram 2: Position & Portfolio Management
- **File**: `02_Position_Portfolio_Management.mmd`
- **Focus**: Position tracking and portfolio metrics
- **Key Classes**: Position, Account, Instrument, PositionService, PortfolioMetrics, PriceService
- **Key Behavior**: Average cost calculation, P&L computation, portfolio aggregation

### Diagram 3: Market Data & Instruments
- **File**: `03_Market_Data_Instruments.mmd`
- **Focus**: Reference data and price information
- **Key Classes**: Instrument, PriceData, InstrumentService, PriceService
- **Key Enum**: AssetClass (EQUITY, BOND, ETF, COMMODITY)

## How They Connect

```
OrderService (Diagram 1)
    ├─ Validates via InstrumentService (Diagram 3)
    ├─ Validates via PriceService (Diagram 3)
    └─ Updates PositionService (Diagram 2)
    
PositionService (Diagram 2)
    ├─ Fetches prices from PriceService (Diagram 3)
    └─ References Instruments (Diagram 3)
    
PriceService (Diagram 3)
    └─ Used by OrderService (Diagram 1)
    └─ Used by PositionService (Diagram 2)
```

