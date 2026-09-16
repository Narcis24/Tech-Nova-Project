```mermaid 
sequenceDiagram
    participant OrderService
    participant Order
    participant Account
    
    OrderService->>Order: getTotalValue()
    Order-->>OrderService: totalValue
    
    alt BUY Order
        OrderService->>Account: debitCash(totalValue)
        Account-->>OrderService: Cash Updated
        
    else SELL Order
        OrderService->>Account: creditCash(totalValue)
        Account-->>OrderService: Cash Updated
    end
    
    OrderService->>Order: execute()
    Order-->>OrderService: status = FILLED