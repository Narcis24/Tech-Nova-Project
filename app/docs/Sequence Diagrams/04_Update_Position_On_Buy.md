```mermaid 

sequenceDiagram 
    participant PositionService
    participant Account
    participant Position
    
    PositionService->>Account: getAccount(accountId)
    Account-->>PositionService: Account
    
    PositionService->>Position: updateOnBuy(quantity, price)
    Position-->>PositionService: Position Updated
```