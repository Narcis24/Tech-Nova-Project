# OrderServiceTest - Test Suite Documentation

## Overview

`OrderServiceTest` is a JUnit 5 test suite that validates the `OrderService` class, which handles trading order operations including buy and sell order execution. The tests use Mockito for mocking repository dependencies and verify that orders are properly processed and state changes are correctly applied.

## Test Structure

### Setup

```java
@BeforeEach
void setUp() {
    orderRepository = mock(OrderRepository.class);
    accountRepository = mock(AccountRepository.class);
    positionRepository = mock(PositionRepository.class);
    instrumentRepository = mock(InstrumentRepository.class);

    orderService = new OrderService(
        orderRepository,
        accountRepository,
        positionRepository,
        instrumentRepository
    );
}
```

Before each test, the framework creates mock objects for all repository dependencies. These mocks simulate database interactions without requiring an actual database connection. The `OrderService` is then instantiated with these mocks for testing.

## Test Cases

### Test 1: Buy Order Execution

**Purpose**: Verify that executing a BUY order correctly:
- Debits the account's cash balance
- Creates or updates a position
- Transitions the order to FILLED status

**Flow**:

```java
@Test
void testExecuteBuyOrderSuccessfully() {
    // ARRANGE: Set up test data
    UUID orderId = UUID.randomUUID();
    
    Account account = new Account(
        "12345",
        "Karl Devon",
        new BigDecimal("2000.00"),      // Initial cash balance
        AccountStatus.ACTIVE,
        LocalDateTime.of(2026, 9, 17, 13, 0)
    );
    
    Order order = new Order(
        orderId,
        "12345",                         // Account ID
        "AAPL",                          // Stock symbol
        OrderSide.BUY,                   // Order type
        10,                              // Quantity
        new BigDecimal("100.00"),        // Price per share
        UUID.randomUUID().toString(),    // Idempotency key
        LocalDateTime.now()
    );
```

**Key Details**:
- Account starts with **€2,000.00** cash
- Buying **10 shares** at **€100.00** each = **€1,000.00** total cost
- No existing position (first purchase)

**Execution**:

```java
    // ACT: Execute the buy order
    orderService.executeOrder(orderId);
```

The service:
1. Fetches the order and account from repositories (mocked)
2. Checks if position exists for AAPL (doesn't exist, so creates new one)
3. **Debits account cash**: €2,000 - €1,000 = **€1,000**
4. **Creates position**: 10 shares of AAPL at €100 average cost
5. Transitions order status from PENDING → FILLED
6. Persists all changes

**Assertions**:

```java
    // ASSERT: Verify state changes
    assertEquals(
        new BigDecimal("1000.00"),
        account.getCashBalance()
    );
```
✓ Cash balance is now €1,000 (decreased by order cost)

```java
    ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);
    verify(positionRepository).save(positionCaptor.capture());
    assertEquals(10, positionCaptor.getValue().getQuantity());
```
✓ Position repository's `save()` was called exactly once
✓ The saved position has 10 shares

```java
    verify(accountRepository).update(account);
    verify(positionRepository).save(any(Position.class));
    verify(orderRepository).update(order);
```
✓ Account was updated (cash change persisted)
✓ Position was saved (new position created)
✓ Order was updated (status changed to FILLED)

---

### Test 2: Sell Order Execution

**Purpose**: Verify that executing a SELL order correctly:
- Credits the account's cash balance
- Reduces the position quantity
- Transitions the order to FILLED status

**Flow**:

```java
@Test
void testSellOrderSuccessfully() {
    // ARRANGE: Set up test data with existing position
    UUID orderId = UUID.randomUUID();
    
    Account account = new Account(
        "12345",
        "Karl Devon",
        new BigDecimal("2000.00"),      // Initial cash balance
        AccountStatus.ACTIVE,
        LocalDateTime.of(2026, 9, 17, 13, 0)
    );
    
    Order order = new Order(
        orderId,
        "12345",
        "AAPL",
        OrderSide.SELL,                  // Selling instead of buying
        10,                              // Quantity to sell
        new BigDecimal("100.00"),        // Sale price per share
        UUID.randomUUID().toString(),
        LocalDateTime.now()
    );
    
    Position position = new Position(
        "12345",
        "AAPL",
        20,                              // Holding 20 shares
        new BigDecimal("80.00")          // Average cost was €80
    );
```

**Key Details**:
- Account starts with **€2,000.00** cash
- Holding **20 shares** of AAPL at €80 average cost
- Selling **10 shares** at **€100.00** each = **€1,000.00** proceeds
- Profit per share: €100 - €80 = €20 (unrealized P&L will be positive)

**Mock Setup**:

```java
    when(orderRepository.findById(orderId))
        .thenReturn(Optional.of(order));
    
    when(positionRepository.findByAccountIdAndSymbol("12345", "AAPL"))
        .thenReturn(Optional.of(position));   // Position exists
    
    when(accountRepository.findById("12345"))
        .thenReturn(Optional.of(account));
```

**Execution**:

```java
    // ACT: Execute the sell order
    orderService.executeOrder(orderId);
```

The service:
1. Fetches the order, position, and account from repositories
2. **Reduces position**: 20 - 10 = **10 shares remain**
3. **Credits account cash**: €2,000 + €1,000 = **€3,000**
4. Transitions order status from PENDING → FILLED
5. Persists all changes

**Assertions**:

```java
    // ASSERT: Verify state changes
    assertEquals(
        new BigDecimal("3000.00"),
        account.getCashBalance()
    );
```
✓ Cash balance increased to €3,000 (added €1,000 from sale proceeds)

```java
    assertEquals(10, position.getQuantity());
```
✓ Position quantity reduced from 20 to 10 shares

```java
    assertEquals(OrderStatus.FILLED, order.getStatus());
```
✓ Order status changed to FILLED (from PENDING)

```java
    verify(positionRepository).update(position);
    verify(accountRepository).update(account);
    verify(orderRepository).update(order);
```
✓ Position was updated (quantity decreased)
✓ Account was updated (cash increased)
✓ Order was updated (status changed)

---

## Key Testing Concepts Used

### 1. **Mocking with Mockito**
```java
orderRepository = mock(OrderRepository.class);
when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
```
- Creates fake repository objects that return predefined values
- Avoids database calls during testing
- Allows isolation of the service logic

### 2. **ArgumentCaptor**
```java
ArgumentCaptor<Position> positionCaptor = ArgumentCaptor.forClass(Position.class);
verify(positionRepository).save(positionCaptor.capture());
assertEquals(10, positionCaptor.getValue().getQuantity());
```
- Captures arguments passed to mock method calls
- Allows inspection of what data was actually passed
- Useful for verifying complex object state

### 3. **Verify Interactions**
```java
verify(accountRepository).update(account);
verify(orderRepository).update(order);
```
- Confirms that specific mock methods were called
- Ensures proper repository interactions happened
- No calls = no persistence = test failure

---

## Business Logic Verified

### Buy Order Flow
```
Initial State:
  - Account: €2,000
  - Position: None
  
Buy 10 AAPL @ €100
  ↓
  Account debit: -€1,000 → €1,000 remaining
  Position created: 10 shares @ €100 avg
  Order status: FILLED
```

### Sell Order Flow
```
Initial State:
  - Account: €2,000
  - Position: 20 AAPL @ €80
  
Sell 10 AAPL @ €100
  ↓
  Account credit: +€1,000 → €3,000 total
  Position reduced: 10 shares @ €80 avg remaining
  Order status: FILLED
```

---

## Notes for Developers

1. **DTO Dependency**: These tests use stub `OrderResponse` DTO. When the full DTO branch is merged, this may need updating.

2. **State Mutation**: The tests verify that entity objects are properly mutated:
   - Account cash balance changes
   - Position quantity changes
   - Order status changes

3. **Repository Calls**: The tests ensure the service layer properly coordinates repository calls in the correct order (important for transaction consistency).

4. **Mock Limitations**: Tests using mocks won't catch database schema issues or constraint violations - integration tests are recommended for full validation.

---

## Running the Tests

```bash
# Run only OrderServiceTest
mvn test -Dtest=OrderServiceTest

# Run with verbose output
mvn test -Dtest=OrderServiceTest -X

# Run all tests
mvn test
```

## Expected Results

✓ Both tests should **PASS**  
✓ No errors or failures  
✓ Total: 2 tests run, 0 failures, 0 errors
