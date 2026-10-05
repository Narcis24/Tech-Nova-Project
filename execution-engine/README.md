# Execution Engine

A small Spring Boot service that stands in for a real exchange. It is step
2 of the order flow (see [`../docs/event-flow.md`](../docs/event-flow.md)):

1. Read an accepted order from the `orders` topic.
2. "Send it to the market": wait a random delay, then fill the whole order
   at a price at or better than its limit.
3. Write the fill to the `executions` topic, keyed by account.

It has no database and no REST API of its own, only an actuator health
endpoint used by the Docker health check.

## What's here

```
execution-engine/
├── Dockerfile                        # multi-stage: Maven build, then JRE 21 runtime
├── pom.xml                           # Spring Boot 3.5, Spring Kafka
└── src/
    ├── main/java/com/neueda/trading/engine/
    │   ├── ExecutionEngineApplication.java
    │   ├── OrderListener.java         # @KafkaListener on orders, publishes to executions
    │   ├── SimulatedMarket.java       # fill delay and fill price
    │   ├── EngineProperties.java      # engine.* settings, validated on startup
    │   ├── EngineConfig.java          # beans + topic declarations
    │   ├── Pauser.java                # the wait, as an interface tests can skip
    │   ├── OrderEvent.java            # message contract: orders topic
    │   ├── ExecutionEvent.java        # message contract: executions topic
    │   └── Side.java
    ├── main/resources/application.yml
    └── test/java/com/neueda/trading/engine/
        ├── SimulatedMarketTest.java   # price never worse than limit, delays in range
        ├── EnginePropertiesTest.java
        ├── OrderListenerTest.java     # listener logic with a mocked KafkaTemplate
        └── ExecutionEngineKafkaTest.java  # whole service against an in-process Kafka
```

## The simulated market

Every order is treated as a limit order and filled in full:

- **Delay:** uniformly random between `engine.min-delay` and
  `engine.max-delay` (default 500 ms to 2 s), so fills visibly lag orders.
- **Price:** a random improvement of 0 to `engine.max-price-improvement-bps`
  basis points (default 50, i.e. 0.5%). A BUY fills at or below its limit
  and a SELL at or above it, so a trader never pays more, or receives less,
  than they asked for. Prices are rounded to 2 decimal places.

`SimulatedMarket` takes its random generator and clock through the
constructor, so tests pin both and assert exact results.

## Reliability

The listener sends the fill and waits for Kafka to acknowledge it before
returning, so the order's offset is only committed once its fill is safely
on `executions`. If the engine stops mid-order, it works that order again
on restart. That can produce a duplicate fill, which the order service
ignores. Unreadable messages are logged and skipped.

The listener runs one consumer thread per partition (3), so up to three
accounts' orders are worked at once.

## Configuration

| Property | Env var | Default |
|----------|---------|---------|
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` |
| `engine.min-delay` | `ENGINE_MIN_DELAY` | `500ms` |
| `engine.max-delay` | `ENGINE_MAX_DELAY` | `2000ms` |
| `engine.max-price-improvement-bps` | `ENGINE_MAX_PRICE_IMPROVEMENT_BPS` | `50` |
| `server.port` | `SERVER_PORT` | `8082` |

In `docker-compose.yml` the engine talks to `kafka:9092`, and the three
`ENGINE_*` values come from `.env`. Set both delays to `0ms` for instant
fills, or both to `10s` to have time to watch orders sit at `NEW`.

## Building and running

```bash
cd execution-engine
mvn test               # unit tests + an in-process Kafka test; no Docker needed
mvn spring-boot:run    # against Kafka on localhost:9094 (docker-compose up -d kafka)
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
