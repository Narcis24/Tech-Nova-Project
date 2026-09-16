```mermaid
sequenceDiagram
    actor User
    participant OrderService
    participant Account
    participant Instrument
    participant Order

    User ->>OrderService: placeOrder(symbol, side, quantity, price)

    OrderService->>Account: validateAccount()
    Account-->>OrderService: ACTIVE

    OrderService->>Instrument: isTradable()
    Instrument-->>OrderService: TRADABLE

    OrderService->>Order: createOrder()

    Order-->>OrderService: status = PENDING
    OrderService-->>User: Order Created

    OrderService-->>User: Order Confirmation
```

    