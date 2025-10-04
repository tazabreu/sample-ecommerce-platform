# E-Commerce Platform

> Headless e-commerce platform with microservices architecture, event-driven order processing, and resilience-first design patterns.

## Overview

This project implements a demonstration-scale e-commerce platform with two microservices:

1. **Customer-Facing Service** (Port 8080): Catalog browsing, shopping cart, and checkout
2. **Order Management Service** (Port 8081): Order processing, payment handling, and fulfillment

### Key Features

- ✅ **Microservices Architecture**: Independent services with domain-driven boundaries
- ✅ **Event-Driven**: Kafka for asynchronous order processing
- ✅ **Resilience Patterns**: Circuit breakers, retries, timeouts (Resilience4j)
- ✅ **Observability**: Prometheus metrics, structured JSON logging, correlation IDs
- ✅ **TDD Approach**: Contract tests, integration tests, unit tests
- ✅ **Production-Ready**: Health checks, graceful shutdown, database migrations

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.2.x
- **Build Tool**: Maven 3.8+
- **Database**: PostgreSQL 15
- **Cache**: Redis 7
- **Messaging**: Apache Kafka (Redpanda for local development)
- **Testing**: JUnit 5, Testcontainers, REST Assured

## Quick Start

### Prerequisites

- Java 21 (OpenJDK or Oracle)
- Maven 3.8+
- Docker & Docker Compose
- 8GB RAM (for Docker containers)

### 1. Start Infrastructure

```bash
cd infrastructure
docker-compose up -d

# Verify services are running
docker-compose ps

# Create Kafka topics
chmod +x kafka/create-topics.sh
./kafka/create-topics.sh
```

### 2. Build Services

```bash
# Build all services
mvn clean install -DskipTests

# Or build individually
cd customer-facing-service && mvn clean install -DskipTests
cd ../order-management-service && mvn clean install -DskipTests
```

### 3. Run Services

**Terminal 1 - Customer Service:**
```bash
cd customer-facing-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Terminal 2 - Order Service:**
```bash
cd order-management-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. Verify Health

```bash
# Customer Service
curl http://localhost:8080/actuator/health

# Order Service
curl http://localhost:8081/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "kafka": {"status": "UP"}
  }
}
```

## API Documentation

Once services are running, access Swagger UI:

- **Customer Service API**: http://localhost:8080/swagger-ui.html
- **Order Service API**: http://localhost:8081/swagger-ui.html

OpenAPI specs:
- Customer Service: http://localhost:8080/v3/api-docs
- Order Service: http://localhost:8081/v3/api-docs

## Project Structure

```
ecommerce-platform/
├── customer-facing-service/       # Catalog, cart, checkout microservice
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ecommerce/customer/
│   │   │   │   ├── config/       # Configuration classes
│   │   │   │   ├── model/        # JPA entities
│   │   │   │   ├── repository/   # Data access layer
│   │   │   │   ├── service/      # Business logic
│   │   │   │   ├── controller/   # REST endpoints
│   │   │   │   ├── dto/          # Data transfer objects
│   │   │   │   ├── event/        # Kafka event publishers
│   │   │   │   └── exception/    # Exception handlers
│   │   │   └── resources/
│   │   │       ├── db/migration/ # Flyway migrations
│   │   │       ├── application.yml
│   │   │       └── logback-spring.xml
│   │   └── test/                  # Tests
│   └── pom.xml
│
├── order-management-service/      # Order processing microservice
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ecommerce/order/
│   │   │   │   ├── config/       # Configuration classes
│   │   │   │   ├── model/        # JPA entities
│   │   │   │   ├── repository/   # Data access layer
│   │   │   │   ├── service/      # Business logic
│   │   │   │   ├── controller/   # REST endpoints
│   │   │   │   ├── dto/          # Data transfer objects
│   │   │   │   ├── event/        # Kafka event consumers
│   │   │   │   ├── payment/      # Payment service
│   │   │   │   └── exception/    # Exception handlers
│   │   │   └── resources/
│   │   │       ├── db/migration/ # Flyway migrations
│   │   │       ├── application.yml
│   │   │       └── logback-spring.xml
│   │   └── test/                  # Tests
│   └── pom.xml
│
├── infrastructure/                 # Docker Compose, Kafka config
│   ├── docker-compose.yml
│   ├── docker-compose.override.yml
│   └── kafka/
│       └── create-topics.sh
│
├── specs/                          # Design documentation
│   └── 001-e-commerce-platform/
│       ├── spec.md                 # Feature specification
│       ├── plan.md                 # Implementation plan
│       ├── data-model.md           # Data model
│       ├── research.md             # Technical decisions
│       ├── quickstart.md           # User journey guide
│       ├── tasks.md                # Task breakdown
│       └── contracts/              # API contracts
│           ├── customer-service-api.yaml
│           ├── order-service-api.yaml
│           └── kafka-events.md
│
├── pom.xml                         # Parent POM
├── .env.example                    # Environment variables template
├── .gitignore
└── README.md                       # This file
```

## Development

### Running Tests

```bash
# Run all tests
mvn test

# Run integration tests
mvn verify

# Run tests for specific service
cd customer-facing-service && mvn test
```

### Database Migrations

Flyway migrations run automatically on application startup. To manually run migrations:

```bash
mvn flyway:migrate
```

### Accessing Databases

```bash
# Customer database
docker exec -it postgres-customer psql -U customer_user -d customer_db

# Order database
docker exec -it postgres-order psql -U order_user -d order_db
```

### Monitoring Kafka

```bash
# List topics
docker exec -it redpanda rpk topic list

# Consume messages
docker exec -it redpanda rpk topic consume orders.created --brokers localhost:9092

# Check consumer group
docker exec -it redpanda rpk group describe order-service-group
```

## Observability

### Prometheus Metrics

Metrics are exposed at:
- Customer Service: http://localhost:8080/actuator/prometheus
- Order Service: http://localhost:8081/actuator/prometheus

Key metrics:
- `http_server_requests_seconds_*` - Request latency and count
- `jvm_memory_*` - JVM memory usage
- `kafka_consumer_*` - Kafka consumer lag
- `resilience4j_circuitbreaker_*` - Circuit breaker state

### Structured Logging

All logs are in JSON format with correlation IDs:

```bash
# View logs with correlation ID
docker logs customer-facing-service | jq 'select(.correlationId=="req-abc123")'

# View error logs
docker logs order-management-service | jq 'select(.level=="ERROR")'
```

## Configuration

See `.env.example` for environment variable configuration.

Key configurations:
- Database connections: `application.yml`
- Kafka topics: `infrastructure/kafka/create-topics.sh`
- Resilience patterns: `config/ResilienceConfig.java`
- Logging: `logback-spring.xml`

## Documentation

- **Feature Specification**: `specs/001-e-commerce-platform/spec.md`
- **Implementation Plan**: `specs/001-e-commerce-platform/plan.md`
- **Data Model**: `specs/001-e-commerce-platform/data-model.md`
- **API Contracts**: `specs/001-e-commerce-platform/contracts/`
- **Quickstart Guide**: `specs/001-e-commerce-platform/quickstart.md`
- **Task Breakdown**: `specs/001-e-commerce-platform/tasks.md`

## Status

**Phase 3.1: Infrastructure Setup** ✅ **COMPLETE**

- [X] Maven multi-module project structure
- [X] Docker Compose infrastructure (PostgreSQL, Redis, Kafka)
- [X] Database migrations (Flyway)
- [X] Application configuration (Spring Boot)
- [X] Resilience patterns (Resilience4j)
- [X] Observability (Micrometer, Actuator)
- [X] Structured logging (Logback with correlation IDs)

**Next Phase**: Contract Tests (TDD approach)

## Contributing

This is a demonstration project following TDD and constitutional development principles. See `specs/001-e-commerce-platform/plan.md` for the complete implementation approach.

## License

This project is for demonstration purposes.

## Contact

For questions or issues, see the project documentation in `specs/001-e-commerce-platform/`.

