```mermaid

sequenceDiagram
    actor User
    participant PositionService
    participant Position
    participant PriceService

    User->>PositionService: getPortfolioSnapshot()

    PositionService->>PriceService: getLatestPrice(symbol)
    PriceService-->>PositionService: latestPrice

    PositionService->>Position: getMarketValue()
    Position-->>PositionService: marketValue

    PositionService-->>User: Portfolio Snapshot
```