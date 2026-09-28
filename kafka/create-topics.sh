#!/bin/bash

set -e

BOOTSTRAP_SERVER="kafka:9092"

echo "Deleting existing topics..."

for topic in trades trade_events market-data; do
    /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "$BOOTSTRAP_SERVER" \
        --delete \
        --topic "$topic" || true
done

echo "Creating topics..."

 /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --topic trades \
    --partitions 3 \
    --replication-factor 1

/opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --topic tradeEvents \
    --partitions 3 \
    --replication-factor 1

/opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --topic marketData \
    --partitions 3 \
    --replication-factor 1

echo "Done."