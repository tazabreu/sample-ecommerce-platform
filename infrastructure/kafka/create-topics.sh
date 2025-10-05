#!/bin/bash

# Kafka Topic Creation Script for E-Commerce Platform
# This script creates the required Kafka topics with appropriate configurations

set -e

KAFKA_CONTAINER="ecommerce-infrastructure-redpanda"
BOOTSTRAP_SERVER="localhost:9092"

echo "Creating Kafka topics for e-commerce platform..."

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
for i in {1..30}; do
    if docker exec $KAFKA_CONTAINER rpk cluster health &>/dev/null; then
        echo "Kafka is ready!"
        break
    fi
    echo "Waiting for Kafka... ($i/30)"
    sleep 2
done

# Create orders.created topic
echo "Creating topic: orders.created"
docker exec $KAFKA_CONTAINER rpk topic create orders.created \
    --partitions 3 \
    --replicas 1 \
    --topic-config retention.ms=604800000 \
    --topic-config compression.type=snappy \
    --topic-config cleanup.policy=delete

# Create payments.completed topic
echo "Creating topic: payments.completed"
docker exec $KAFKA_CONTAINER rpk topic create payments.completed \
    --partitions 3 \
    --replicas 1 \
    --topic-config retention.ms=604800000 \
    --topic-config compression.type=snappy \
    --topic-config cleanup.policy=delete

# Create dead letter queue topics
echo "Creating DLQ topic: orders.created.dlq"
docker exec $KAFKA_CONTAINER rpk topic create orders.created.dlq \
    --partitions 1 \
    --replicas 1 \
    --topic-config retention.ms=2592000000 \
    --topic-config cleanup.policy=delete

echo "Creating DLQ topic: payments.completed.dlq"
docker exec $KAFKA_CONTAINER rpk topic create payments.completed.dlq \
    --partitions 1 \
    --replicas 1 \
    --topic-config retention.ms=2592000000 \
    --topic-config cleanup.policy=delete

# List all topics
echo ""
echo "All topics:"
docker exec $KAFKA_CONTAINER rpk topic list

echo ""
echo "Topic creation complete!"

