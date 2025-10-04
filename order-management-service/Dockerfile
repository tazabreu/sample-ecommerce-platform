# syntax=docker/dockerfile:1

# Stage 1: Cache Maven dependencies
FROM maven:3.9.4-eclipse-temurin-21 AS deps
WORKDIR /build
COPY pom.xml .
COPY customer-facing-service/pom.xml customer-facing-service/
COPY order-management-service/pom.xml order-management-service/
COPY shared-lib/pom.xml shared-lib/
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -DskipTests

# Stage 2: Build the application
FROM deps AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -Dmaven.test.skip=true -pl order-management-service -am

# Stage 3: Extract layers for better caching
FROM build AS extract
WORKDIR /build
RUN java -Djarmode=layertools -jar order-management-service/target/order-management-service-1.0.0-SNAPSHOT.jar extract --destination target/extracted

# Stage 4: Create runtime image with minimal base
FROM eclipse-temurin:21-jre-alpine AS final

# Add metadata labels
LABEL org.opencontainers.image.title="Order Management Service"
LABEL org.opencontainers.image.description="E-commerce order management microservice"
LABEL org.opencontainers.image.version="1.0.0-SNAPSHOT"
LABEL org.opencontainers.image.vendor="E-Commerce Platform"

# Install curl for healthchecks
RUN apk add --no-cache curl

WORKDIR /app

# Copy extracted layers for optimal layer caching
COPY --from=extract --chown=nobody:nobody /build/target/extracted/dependencies/ ./
COPY --from=extract --chown=nobody:nobody /build/target/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=nobody:nobody /build/target/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=nobody:nobody /build/target/extracted/application/ ./

# Use existing nobody user for security
USER nobody

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Use shell form to allow environment variable expansion
ENTRYPOINT sh -c "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"


