```mermaid

sequenceDiagram
    participant PositionService
    participant Position
    
    PositionService->>Position: updateOnSell(quantity, price)
    Position-->>PositionService: Position Updated


```