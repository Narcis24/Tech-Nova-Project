# End-to-End Testing Guide: Kafka Order Execution

## Prerequisites
- Docker & Docker Compose installed
- PostgreSQL client (psql) or DBeaver for database inspection
- curl or Postman for API testing
- Terminal with multiple tabs

## 1. Start the System

```bash
# In the Tech-Nova-Project root directory
docker-compose up -d

# Verify all services are healthy
docker-compose ps

# Expected output:
# tech-nova-kafka-ui      kafbat/kafka-ui:v1.5.0   UP (healthy)
# tech-nova-kafka         apache/kafka:4.3.1       UP (healthy)
# tech-nova-app           (custom build)             UP
# tech-nova-execution-engine (custom build)          UP
# tech-nova-db            postgres:16-alpine        UP (healthy)
```

## 2. Monitor Logs in Real-Time

Open separate terminals for each service:

```bash
# Terminal 1: App logs
docker-compose logs -f app

# Terminal 2: Execution Engine logs
docker-compose logs -f execution-engine

# Terminal 3: Database logs (optional)
docker-compose logs -f db

# Terminal 4: Kafka container (optional)
docker-compose logs -f kafka
```

## 3. Authentication Setup (Required!)

### Step 1: Register a Test User

```bash
curl -X POST http://localhost:8082/auth/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "TestPass123@"
  }'

# Password requirements: 12-20 chars, uppercase, lowercase, digit, special char
```

### Step 2: Login to Get JWT Token

```bash
TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "TestPass123@"
  }' | jq -r '.token')

echo "JWT Token: $TOKEN"
```

**Token Details:**
- Expires in 1 hour (3600000ms)
- Used in `Authorization: Bearer <token>` header for all order API calls

### Step 3: Get an Account

An account belongs to the user who opened it, and every other user gets `403 ACCESS_DENIED`.
The seed accounts (ACC001-ACC005) start with no owner, so claim them for your user to run the
examples below as written:

```bash
./db/scripts/claim-seed-accounts.sh testuser
```

Or open a fresh account of your own (it starts at $0) and use its id in place of ACC001:

```bash
ACCOUNT=$(curl -s -X POST http://localhost:8081/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"holderName": "Test User"}' | jq -r '.accountId')

curl -s -X POST http://localhost:8081/api/v1/accounts/$ACCOUNT/deposit \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount": 50000}'

# list your accounts
curl -s http://localhost:8081/api/v1/accounts -H "Authorization: Bearer $TOKEN"
```

---

## 4. Test the Complete Flow

### Step 1: Place a BUY Order (with Authentication)

```bash
TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPass123@"}' | jq -r '.token')

curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "accountId": "ACC001",
    "symbol": "AAPL",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 100,
    "price": 150.00,
    "idempotencyKey": "order-001-test"
  }'
```

**Expected Response:**
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "accountId": "ACC001",
  "symbol": "AAPL",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 100,
  "price": 150.00,
  "status": "PUBLISHED",
  "message": null
}
```

**Important Fields:**
- `status`: `PUBLISHED` means order was accepted and sent to execution engine
- `idempotencyKey`: Required field - prevents duplicate orders (unique identifier)

**What happens:**
- App creates order in DB with `PENDING` status
- App publishes to `order-requests` topic → status changes to `PUBLISHED`
- **Check App logs:** Should see "Published order..."

### Step 2: Watch Execution Engine Process

**Check Execution Engine logs:** You should see:
```
Working order 550e8400-e29b-41d4-a716-446655440000: BUY 100 AAPL limit 150.00
Filled order 550e8400-e29b-41d4-a716-446655440000 at 149.95 on SIM
```

**Timeline:** ~500-2000ms delay (configurable via `ENGINE_MIN_DELAY`, `ENGINE_MAX_DELAY`)

### Step 3: Verify Settlement

**Check App logs:** Should see:
```
Received execution for order 550e8400-e29b-41d4-a716-446655440000 from SIM
Successfully settled order 550e8400-e29b-41d4-a716-446655440000 at 149.95
```

### Step 4: Verify Database State

```bash
# Connect to the database
docker-compose exec -T db psql -U technova -d technova

# Check the order status
SELECT id, account_id, symbol, status, price, quantity FROM orders 
WHERE account_id = 'ACC001' 
ORDER BY created_on DESC LIMIT 1;

# Expected: status = 'FILLED'
```

### Step 5: Verify Position and Cash Updated

```sql
-- Check position was created/updated
SELECT account_id, symbol, quantity, average_cost 
FROM positions 
WHERE account_id = 'ACC001' AND symbol = 'AAPL';

-- Expected: quantity increased, average_cost ≈ execution price

-- Check cash was debited
SELECT account_id, holder_name, cash_balance 
FROM accounts 
WHERE account_id = 'ACC001';

-- Expected: cash_balance reduced by (quantity * execution_price)
```

---

## 5. Monitor Kafka Topics

### Using Kafka UI
```
Open: http://localhost:8090
Navigate to: tech-nova cluster → Topics
```

**Inspect Topics:**
- `order-requests`: Should see your BUY order message
- `order-executions`: Should see the execution fill message

### Using Command Line
```bash
# List all topics
docker exec -it tech-nova-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# Consume from order-requests (from beginning)
docker exec -it tech-nova-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-requests \
  --from-beginning

# Consume from order-executions (from beginning)
docker exec -it tech-nova-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-executions \
  --from-beginning
```

## 5. Test Different Scenarios

### Scenario A: MARKET Order
```bash
TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPass123@"}' | jq -r '.token')

curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "accountId": "ACC001",
    "symbol": "GOOGL",
    "side": "BUY",
    "orderType": "MARKET",
    "quantity": 50,
    "idempotencyKey": "market-order-001"
  }'

# Expected: Filled immediately with current market price
# Note: Price field is NOT included for MARKET orders
```

### Scenario B: SELL Order (requires existing position)
```bash
TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPass123@"}' | jq -r '.token')

curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "accountId": "ACC001",
    "symbol": "AAPL",
    "side": "SELL",
    "orderType": "LIMIT",
    "quantity": 50,
    "price": 155.00,
    "idempotencyKey": "sell-order-001"
  }'

# Expected: Position reduced by 50, cash increased
```

### Scenario C: Cancel Order
```bash
TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPass123@"}' | jq -r '.token')

curl -X DELETE http://localhost:8081/api/v1/orders/{orderId} \
  -H "Authorization: Bearer $TOKEN"

# Expected: status = 'CANCELLED'
# Replace {orderId} with actual order ID from earlier responses
```

---

## 6. Verify Idempotency

Test duplicate protection with idempotency key:

```bash
TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPass123@"}' | jq -r '.token')

# First call - succeeds
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "accountId": "ACC001",
    "symbol": "MSFT",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 10,
    "price": 300.00,
    "idempotencyKey": "unique-key-12345"
  }'

# Second call with same idempotencyKey - fails
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "accountId": "ACC001",
    "symbol": "MSFT",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 10,
    "price": 300.00,
    "idempotencyKey": "unique-key-12345"
  }'

# Expected: Error "Order already submitted with idempotency key: unique-key-12345"
```

---

## 7. Test Failure Scenarios

### Scenario: Insufficient Funds
```bash
TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPass123@"}' | jq -r '.token')

curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "accountId": "ACC001",
    "symbol": "BRK",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 10000,
    "price": 500000.00,
    "idempotencyKey": "insufficient-funds-test"
  }'

# Expected: Order accepted with PUBLISHED status, but settlement fails
# Check app logs for: "Insufficient cash balance"
# Order will remain in PUBLISHED state (execution-engine cannot fill it)
```

### Scenario: Insufficient Holdings (SELL without position)
```bash
TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPass123@"}' | jq -r '.token')

curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "accountId": "ACC001",
    "symbol": "UNKNOWN",
    "side": "SELL",
    "orderType": "MARKET",
    "quantity": 100,
    "idempotencyKey": "insufficient-holdings-test"
  }'

# Expected: Either validation error or order stuck in PUBLISHED
# Check app logs for: "No position in UNKNOWN for account ACC001"
```

### Scenario: Missing JWT Token
```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC001",
    "symbol": "AAPL",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 100,
    "price": 150.00,
    "idempotencyKey": "no-token-test"
  }'

# Expected: 401 Unauthorized - "Missing or invalid JWT token"
```

---

## 8. Test Restart/Recovery

### Scenario: Kill Execution Engine & Place Orders

```bash
# 1. Stop the execution engine
docker-compose stop execution-engine

# 2. Place orders while engine is down
TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPass123@"}' | jq -r '.token')

curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "accountId": "ACC001",
    "symbol": "TSLA",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 25,
    "price": 200.00,
    "idempotencyKey": "recovery-test-1"
  }'

# 3. Restart engine
docker-compose up -d execution-engine

# 4. Wait 3-5 seconds
sleep 5

# 5. Check app logs
docker-compose logs -f execution-engine
```

**Expected:** 
- Orders remain in `PUBLISHED` status while engine is down
- When engine restarts, it consumes messages from Kafka from its last offset
- Orders should be filled within 500-2000ms after engine starts

## 9. Automated Test Script

Save as `test_order_flow.sh`:

```bash
#!/bin/bash
set -e

AUTH_URL="http://localhost:8082/auth/v1"
API_URL="http://localhost:8081/api/v1"
ACCOUNT_ID="ACC001"

echo "🚀 Starting End-to-End Order Flow Test"

# Step 1: Register test user
echo "1️⃣  Registering test user..."
REGISTER_RESPONSE=$(curl -s -X POST "$AUTH_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser2","password":"TestPass123@"}')

if echo "$REGISTER_RESPONSE" | grep -q "already exists\|registered successfully"; then
  echo "   ✓ User registered or already exists"
else
  echo "   ✗ Registration failed: $REGISTER_RESPONSE"
fi

# Step 2: Login to get JWT token
echo "2️⃣  Logging in to get JWT token..."
TOKEN=$(curl -s -X POST "$AUTH_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser2","password":"TestPass123@"}' | jq -r '.token')

if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
  echo "   ✗ Failed to get JWT token"
  exit 1
fi
echo "   ✓ JWT Token obtained: ${TOKEN:0:20}..."

# Step 3: Place BUY order
echo "3️⃣  Placing BUY order..."
IDEMPOTENCY_KEY="order-$(date +%s)"
ORDER_RESPONSE=$(curl -s -X POST "$API_URL/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"accountId\": \"$ACCOUNT_ID\",
    \"symbol\": \"AAPL\",
    \"side\": \"BUY\",
    \"orderType\": \"LIMIT\",
    \"quantity\": 100,
    \"price\": 150.00,
    \"idempotencyKey\": \"$IDEMPOTENCY_KEY\"
  }")

ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.orderId // .id // empty')
if [ -z "$ORDER_ID" ]; then
  echo "   ✗ Order placement failed: $ORDER_RESPONSE"
  exit 1
fi

STATUS=$(echo "$ORDER_RESPONSE" | jq -r '.status')
echo "   ✓ Order created: $ORDER_ID"
echo "   ✓ Status: $STATUS"

# Step 4: Wait for execution engine
echo "4️⃣  Waiting for execution engine to process (5 seconds)..."
sleep 5

# Step 5: Check order status in database
echo "5️⃣  Checking final order status..."
FINAL_STATUS=$(docker-compose exec -T db psql -U technova -d technova \
  -c "SELECT status FROM orders WHERE id='$ORDER_ID';" 2>/dev/null | tail -1 | xargs)

if [ "$FINAL_STATUS" == "FILLED" ]; then
  echo "   ✓ Order successfully FILLED!"
  echo "✅ All tests passed!"
  exit 0
elif [ "$FINAL_STATUS" == "PUBLISHED" ]; then
  echo "   ⚠ Order still in PUBLISHED state (execution pending)"
  echo "❌ Test failed - order not filled in time"
  exit 1
else
  echo "   ✗ Unexpected status: $FINAL_STATUS"
  exit 1
fi
```

**Make script executable:**
```bash
chmod +x test_order_flow.sh
./test_order_flow.sh
```

---

## 10. Access Endpoints Summary

| Service | URL | Purpose |
|---------|-----|---------|
| **API Documentation** | http://localhost:8081/swagger-ui.html | Order API Swagger UI |
| **Auth Service** | http://localhost:8082/auth/swagger-ui/index.html | Authentication Swagger UI |
| **Kafka UI** | http://localhost:8090 | Monitor topics and messages |
| **Order API** | http://localhost:8081/api/v1/orders | Place/cancel orders (requires JWT) |
| **Auth API** | http://localhost:8082/auth/v1/auth | Register/login endpoints |
| **Database** | localhost:5434 | PostgreSQL (User: technova) |

---

## 11. Key Things to Look For

| Component | What to Check | Success Criteria |
|-----------|---------------|------------------|
| **Auth Service** | POST /auth/login returns JWT token | Token present in response |
| **Order API** | POST /orders requires Authorization header | 401 error without token |
| **Order API** | POST /orders returns PUBLISHED status | Order saved, status is PUBLISHED |
| **Kafka Topics** | `order-requests` has message | Message appears within 1s |
| **Execution Engine** | Logs show "Filled order..." | Engine processed and sent result |
| **Kafka Topics** | `order-executions` has fill message | Fill message appears 500-2000ms later |
| **ExecutionListener** | App logs show "Received execution..." | Listener consumed the message |
| **Database** | Order status = FILLED | Status updated to FILLED |
| **Database** | Position created/updated | Quantity increased, avg cost set |
| **Database** | Account cash debited/credited | Cash balance changed correctly |

---

## 12. Cleanup

```bash
# Stop all services
docker-compose down

# Remove volumes (CAUTION: deletes all data)
docker-compose down -v
```

---

## 13. Troubleshooting Guide

### ❌ 401 Unauthorized - "Missing or invalid JWT token"

**Problem:** Order API returns 401 error
```json
{"errorCode":"UNAUTHORIZED","message":"Missing or invalid JWT token"}
```

**Solutions:**
1. Verify you include the JWT token in the Authorization header:
   ```bash
   curl -H "Authorization: Bearer $TOKEN" ...
   ```
   (Note: It's `Bearer`, not `Basic`)

2. Check if token is expired (1 hour expiration):
   ```bash
   # Get a fresh token
   TOKEN=$(curl -s -X POST http://localhost:8082/auth/v1/auth/login ...)
   ```

3. Verify auth service is running:
   ```bash
   docker-compose logs auth-service
   ```

### ❌ 400 Bad Request - "idempotencyKey: Idempotency key is required"

**Problem:** Order API returns 400 error
```json
{"errorCode":"VALIDATION_ERROR","message":"idempotencyKey: Idempotency key is required"}
```

**Solution:** Include `idempotencyKey` field in every order request:
```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "accountId": "ACC001",
    "symbol": "AAPL",
    ...
    "idempotencyKey": "unique-key-12345"  # ← Required!
  }'
```

### ❌ 400 Bad Request - Database Constraint Violation

**Problem:** 500 error with log:
```
ERROR: new row for relation "orders" violates check constraint "chk_orders_status"
```

**Solution:** The database constraint was missing the `PUBLISHED` status. Update it:
```bash
docker-compose exec -T db psql -U technova -d technova << EOF
ALTER TABLE orders DROP CONSTRAINT chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status 
  CHECK (status IN ('PENDING', 'PUBLISHED', 'FILLED', 'CANCELLED', 'REJECTED', 'PARTIALLY_FILLED', 'EXPIRED'));
EOF
```

### ❌ Order stays PUBLISHED (not being filled)

**Problem:** Order remains in PUBLISHED status after 5+ seconds

**Debug Steps:**

1. **Check execution-engine is running:**
   ```bash
   docker-compose ps | grep execution-engine
   # Should show "Up" status
   ```

2. **Check execution-engine logs:**
   ```bash
   docker-compose logs -f execution-engine | tail -50
   # Look for "Working order..." and "Filled order..." messages
   ```

3. **Verify Kafka connectivity:**
   ```bash
   docker-compose logs execution-engine 2>&1 | grep -i "kafka\|connect\|error" | tail -10
   ```

4. **Check if order even reached Kafka:**
   - Open http://localhost:8090 (Kafka UI)
   - Navigate to `tech-nova` cluster → Topics
   - Check `order-requests` topic for your order message

5. **Restart execution-engine:**
   ```bash
   docker-compose restart execution-engine
   sleep 5
   docker-compose logs execution-engine
   ```

### ❌ ExecutionListener not consuming messages

**Problem:** App logs show order published, but no "Received execution" log

**Debug Steps:**

1. **Verify listener is registered:**
   ```bash
   docker-compose logs app 2>&1 | grep -i "ExecutionListener\|@KafkaListener"
   ```

2. **Check consumer group lag:**
   ```bash
   docker-compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 \
     --group tech-nova-group \
     --describe
   # LAG should be 0 or increasing slowly
   ```

3. **Check if messages exist on topic:**
   ```bash
   docker-compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 \
     --topic order-executions \
     --from-beginning \
     --max-messages 5
   # Should show JSON messages with execution data
   ```

### ❌ Position/Cash not updating after order filled

**Problem:** Order shows FILLED but position/cash unchanged in database

**Debug Steps:**

1. **Check OrderSettlementService logs:**
   ```bash
   docker-compose logs app 2>&1 | grep -i "settlement\|position\|cash"
   ```

2. **Verify order was actually filled:**
   ```bash
   docker-compose exec -T db psql -U technova -d technova \
     -c "SELECT id, status FROM orders WHERE account_id='ACC001' ORDER BY created_on DESC LIMIT 1;"
   ```

3. **Check if position exists:**
   ```bash
   docker-compose exec -T db psql -U technova -d technova \
     -c "SELECT * FROM positions WHERE account_id='ACC001';"
   ```

4. **Check account cash balance:**
   ```bash
   docker-compose exec -T db psql -U technova -d technova \
     -c "SELECT account_id, cash_balance FROM accounts WHERE account_id='ACC001';"
   ```

### ❌ Kafka topics not created

**Problem:** Kafka UI shows no topics or topics are empty

**Debug Steps:**

1. **Check Kafka is healthy:**
   ```bash
   docker-compose ps | grep kafka
   # Should show healthy status
   ```

2. **Verify topics exist:**
   ```bash
   docker-compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
     --bootstrap-server localhost:9092 \
     --list
   # Should show: order-requests, order-executions
   ```

3. **Create topics manually if missing:**
   ```bash
   docker-compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
     --bootstrap-server localhost:9092 \
     --create --if-not-exists \
     --topic order-requests \
     --partitions 3 --replication-factor 1
   
   docker-compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
     --bootstrap-server localhost:9092 \
     --create --if-not-exists \
     --topic order-executions \
     --partitions 3 --replication-factor 1
   ```

4. **Restart app to trigger topic creation:**
   ```bash
   docker-compose restart app
   sleep 5
   # Topics should be auto-created by KafkaConfig
   ```

### ❌ Database constraint/migration issues

**Problem:** Order creation fails with constraint or validation errors

**Solutions:**

1. **Check all constraints are in place:**
   ```bash
   docker-compose exec -T db psql -U technova -d technova -c "\d orders"
   # Verify all CHECK constraints exist
   ```

2. **Verify migrations ran:**
   ```bash
   docker-compose exec -T db psql -U technova -d technova \
     -c "SELECT name FROM flyway_schema_history ORDER BY success DESC;"
   ```

3. **Check migration files:**
   ```bash
   ls -la db/migrations/
   # Should show V001 through V007 (or latest)
   ```

### ⚠️ Performance/Timeout Issues

**Problem:** Orders taking longer than 5 seconds to fill

**Possible Causes:**

1. **Engine delay configuration:** Execution engine has configurable random delay:
   ```bash
   # Check current settings
   docker-compose exec -T execution-engine env | grep ENGINE
   # Default: MIN_DELAY=500ms, MAX_DELAY=2000ms
   ```

2. **Kafka message batching:** Messages may be batched for efficiency
   - This is normal behavior
   - Verify in logs it's processing

3. **Database transaction locks:** Multiple concurrent orders
   - Check database logs for lock waits
   - Serial processing may be normal

---

## 14. Quick Debug Commands

**Health check all services:**
```bash
docker-compose ps
docker-compose logs --tail=5 app auth-service execution-engine
```

**View recent orders:**
```bash
docker-compose exec -T db psql -U technova -d technova \
  -c "SELECT id, account_id, symbol, status FROM orders ORDER BY created_on DESC LIMIT 10;"
```

**Monitor messages in real-time:**
```bash
# Terminal 1: Watch order requests
docker-compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order-requests

# Terminal 2: Watch order executions
docker-compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order-executions
```

**Clear all data and restart (CAUTION):**
```bash
docker-compose down -v
docker-compose up -d --build
sleep 30  # Wait for all services to start
echo "System ready!"
```
