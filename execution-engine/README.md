# Execution Engine

A small Spring Boot service that stands in for a real exchange:

1. Read `ORDER_PLACED` events from the `order-request` topic.
2. Price them against live quotes from the `market-data` topic (see below).
3. Publish `ORDER_EXECUTED` or `ORDER_REJECTED` to `order-execution`, keyed by order id,
   where the app settles or rejects the order.

It has no database and no REST API of its own, only an actuator health
endpoint used by the Docker health check.

## What's here

```
execution-engine/
├── Dockerfile                        # multi-stage: Maven build, then JRE 21 runtime; build from the repo root
├── pom.xml                           # Spring Boot 4.1, Spring Kafka
└── src/
    ├── main/java/com/neueda/trading/
    │   ├── ExecutionEngineApplication.java
    │   ├── engine/
    │   │   ├── ExecutionEngine.java       # @KafkaListeners for orders and quotes, resting LIMIT orders
    │   │   ├── FillRule.java              # pure fill/wait/reject decision for an order and a quote
    │   │   ├── MarketDataPoller.java      # scheduled Alpaca fetch, publishes to market-data
    │   │   ├── AlpacaQuoteClient.java     # QuoteClient for Alpaca's latest-quotes endpoint
    │   │   ├── QuoteCache.java            # latest quote per symbol
    │   │   ├── MarketDataProperties.java  # market-data.* settings, quota checked on startup
    │   │   ├── EventProducerService.java  # wraps payloads in EventEnvelope and sends them
    │   │   └── KafkaConfig.java           # retry + dead-letter handler, topic declarations
        │   └── enums/Side.java
    ├── main/resources/application.yml
    └── test/java/com/neueda/trading/engine/
        ├── FillRuleTest.java
        ├── ExecutionEngineTest.java
        └── MarketDataTest.java
```

## Reliability

A listener that throws is retried 3 times, 1 second apart, then the message goes to
`<topic>-dlt`. Sends are not awaited, so a failed send is only logged. LIMIT orders that
are waiting for the market live in memory and are lost if the engine restarts.

## Configuration

| Property | Env var | Default |
|----------|---------|---------|
| `spring.kafka.bootstrap-servers` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` |
| `market-data.interval-seconds` | `MARKET_DATA_INTERVAL_SECONDS` | `120` |
| `market-data.key-id` | `ALPACA_KEY_ID` | empty |
| `market-data.secret-key` | `ALPACA_SECRET_KEY` | empty |
| `server.port` | `SERVER_PORT` | `8082` |

## Building and running

The message contracts (`EventEnvelope`, `OrderPlacedEvent`, `Topics`, ...) live in the shared
`messaging` module, so build from the repo root:

```bash
mvn test -pl execution-engine -am   # unit tests, no Kafka or Docker needed
```

Through Docker, it runs as the `execution-engine` service:

```bash
docker-compose up --build -d execution-engine
docker-compose logs -f execution-engine
```


## Market data and pricing

`MarketDataPoller` fetches the latest quotes from Alpaca (IEX feed), at most 25 symbols per
request, and publishes one `MARKET_DATA` message per symbol to `market-data`, keyed by symbol.
`QuoteCache` keeps the latest quote per symbol from that topic.

`ExecutionEngine` prices each `ORDER_PLACED` with `FillRule.decide(order, quote)`, a pure
function: a BUY trades at the ask and a SELL at the bid; a MARKET order fills there, a LIMIT
order only if that price is at or better than its limit. A LIMIT that has not crossed yet
(or has no quote yet) rests in memory; every new quote for its symbol re-checks it, so there
is no extra schedule. Resting orders are lost if the executor restarts. A MARKET order with no
fresh quote (`market-data.max-quote-age-seconds`), or a malformed order, is published as
`ORDER_REJECTED`, which the app turns into a REJECTED order. A resting order that was
cancelled in the meantime is harmless: the app ignores a fill for an order that is no longer PENDING.

**Quota:** requests/day = ceil(symbols / 25) x ceil(86400 / interval). 39 symbols at the
default 120 s is 2 x 720 = 1,440 of the 2,000 allowed. Startup fails if the configured
symbols and interval would exceed `market-data.daily-quota`.

Set `ALPACA_KEY_ID` and `ALPACA_SECRET_KEY` for the poller.
