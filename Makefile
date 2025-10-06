# E-Commerce Platform Makefile
# Follows excellent practices for maintainability and usability

# Variables
MAVEN := ./mvnw

DOCKER_COMPOSE := docker-compose
CUSTOMER_SERVICE := customer-facing-service
ORDER_SERVICE := order-management-service
SHARED_LIB := shared-lib

# Colors for output
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
NC := \033[0m # No Color

# Default target
.DEFAULT_GOAL := help

# Help target
.PHONY: help
help: ## Show this help message
	@echo "$(BLUE)E-Commerce Platform Build System$(NC)"
	@echo ""
	@echo "Available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-20s$(NC) %s\n", $$1, $$2}'

# Development targets
.PHONY: clean
clean: ## Clean all build artifacts
	@echo "$(YELLOW)Cleaning all build artifacts...$(NC)"
	$(MAVEN) clean
	@echo "$(GREEN)Clean completed.$(NC)"

.PHONY: compile
compile: ## Compile all services
	@echo "$(YELLOW)Compiling all services...$(NC)"
	$(MAVEN) compile
	@echo "$(GREEN)Compilation completed.$(NC)"

# Test targets
.PHONY: test
test: test-all ## Run all tests (alias for test-all)

.PHONY: test-all
test-all: ## Run all tests across all services
	@echo "$(YELLOW)Running all tests...$(NC)"
	$(MAVEN) test
	@echo "$(GREEN)All tests completed.$(NC)"

.PHONY: test-customer
test-customer: ## Run tests for customer-facing-service only
	@echo "$(YELLOW)Running customer-facing-service tests...$(NC)"
	$(MAVEN) test -pl $(CUSTOMER_SERVICE)
	@echo "$(GREEN)Customer service tests completed.$(NC)"

.PHONY: test-order
test-order: ## Run tests for order-management-service only
	@echo "$(YELLOW)Running order-management-service tests...$(NC)"
	$(MAVEN) test -pl $(ORDER_SERVICE)
	@echo "$(GREEN)Order service tests completed.$(NC)"

.PHONY: test-shared
test-shared: ## Run tests for shared-lib only
	@echo "$(YELLOW)Running shared-lib tests...$(NC)"
	$(MAVEN) test -pl $(SHARED_LIB)
	@echo "$(GREEN)Shared library tests completed.$(NC)"

# Build targets
.PHONY: build
build: build-all ## Build all services (alias for build-all)

.PHONY: build-all
build-all: ## Build all services and run tests
	@echo "$(YELLOW)Building all services...$(NC)"
	$(MAVEN) clean compile test
	@echo "$(GREEN)All services built successfully.$(NC)"

.PHONY: build-customer
build-customer: ## Build customer-facing-service only
	@echo "$(YELLOW)Building customer-facing-service...$(NC)"
	$(MAVEN) clean compile test -pl $(CUSTOMER_SERVICE)
	@echo "$(GREEN)Customer service built successfully.$(NC)"

.PHONY: build-order
build-order: ## Build order-management-service only
	@echo "$(YELLOW)Building order-management-service...$(NC)"
	$(MAVEN) clean compile test -pl $(ORDER_SERVICE)
	@echo "$(GREEN)Order service built successfully.$(NC)"

.PHONY: build-shared
build-shared: ## Build shared-lib only
	@echo "$(YELLOW)Building shared-lib...$(NC)"
	$(MAVEN) clean compile test -pl $(SHARED_LIB)
	@echo "$(GREEN)Shared library built successfully.$(NC)"

# Package targets
.PHONY: package
package: package-all ## Package all services (alias for package-all)

.PHONY: package-all
package-all: ## Package all services (creates JARs)
	@echo "$(YELLOW)Packaging all services...$(NC)"
	$(MAVEN) clean package -DskipTests
	@echo "$(GREEN)All services packaged successfully.$(NC)"

.PHONY: package-customer
package-customer: ## Package customer-facing-service only
	@echo "$(YELLOW)Packaging customer-facing-service...$(NC)"
	$(MAVEN) clean package -DskipTests -pl $(CUSTOMER_SERVICE)
	@echo "$(GREEN)Customer service packaged successfully.$(NC)"

.PHONY: package-order
package-order: ## Package order-management-service only
	@echo "$(YELLOW)Packaging order-management-service...$(NC)"
	$(MAVEN) clean package -DskipTests -pl $(ORDER_SERVICE)
	@echo "$(GREEN)Order service packaged successfully.$(NC)"

# Verification and quality targets
.PHONY: verify
verify: ## Run full verification (tests, integration tests, checkstyle, etc.)
	@echo "$(YELLOW)Running full verification...$(NC)"
	$(MAVEN) clean verify
	@echo "$(GREEN)Verification completed.$(NC)"

.PHONY: coverage
coverage: ## Generate code coverage reports
	@echo "$(YELLOW)Generating code coverage reports...$(NC)"
	$(MAVEN) clean test jacoco:report-aggregate
	@echo "$(GREEN)Coverage reports generated.$(NC)"
	@echo "$(BLUE)Coverage reports available at:$(NC)"
	@echo "  - target/site/jacoco-aggregate/index.html"
	@echo "  - $(CUSTOMER_SERVICE)/target/site/jacoco/index.html"
	@echo "  - $(ORDER_SERVICE)/target/site/jacoco/index.html"

# Docker targets
.PHONY: docker-build
docker-build: ## Build Docker images for all services
	@echo "$(YELLOW)Building Docker images...$(NC)"
	$(DOCKER_COMPOSE) build
	@echo "$(GREEN)Docker images built successfully.$(NC)"

.PHONY: docker-up
docker-up: ## Start all services with Docker Compose
	@echo "$(YELLOW)Starting services with Docker Compose...$(NC)"
	$(DOCKER_COMPOSE) up -d
	@echo "$(GREEN)Services started. Use 'make docker-logs' to view logs.$(NC)"

.PHONY: docker-down
docker-down: ## Stop all services with Docker Compose
	@echo "$(YELLOW)Stopping services with Docker Compose...$(NC)"
	$(DOCKER_COMPOSE) down
	@echo "$(GREEN)Services stopped.$(NC)"

.PHONY: docker-logs
docker-logs: ## Show logs from Docker Compose services
	$(DOCKER_COMPOSE) logs -f

.PHONY: docker-restart
docker-restart: docker-down docker-up ## Restart all services

# Manual testing targets
.PHONY: manual-test
manual-test: ## Run manual tests using the Python test runner
	@echo "$(YELLOW)Running manual tests...$(NC)"
	cd manual-tests && python3 test_runner.py
	@echo "$(GREEN)Manual tests completed.$(NC)"

.PHONY: manual-test-setup
manual-test-setup: ## Set up manual test environment
	@echo "$(YELLOW)Setting up manual test environment...$(NC)"
	cd manual-tests && python3 -m venv venv && source venv/bin/activate && pip install -r requirements.txt
	@echo "$(GREEN)Manual test environment set up.$(NC)"

# Development workflow targets
.PHONY: dev-setup
dev-setup: ## Set up development environment
	@echo "$(YELLOW)Setting up development environment...$(NC)"
	@echo "Installing Maven wrapper..."
	$(MAVEN) -N io.takari:maven:wrapper
	@echo "Setting up manual tests..."
	$(MAKE) manual-test-setup
	@echo "$(GREEN)Development environment set up.$(NC)"

.PHONY: dev-clean
dev-clean: clean ## Clean everything including Docker containers
	@echo "$(YELLOW)Cleaning Docker containers...$(NC)"
	-$(DOCKER_COMPOSE) down -v --remove-orphans
	-docker system prune -f
	@echo "$(GREEN)Full cleanup completed.$(NC)"

# Quick development targets
.PHONY: quick-test
quick-test: ## Run tests without clean (faster for development)
	@echo "$(YELLOW)Running tests (no clean)...$(NC)"
	$(MAVEN) test
	@echo "$(GREEN)Tests completed.$(NC)"

.PHONY: quick-build
quick-build: ## Build without clean (faster for development)
	@echo "$(YELLOW)Building (no clean)...$(NC)"
	$(MAVEN) compile test
	@echo "$(GREEN)Build completed.$(NC)"

# Utility targets
.PHONY: info
info: ## Show project information
	@echo "$(BLUE)E-Commerce Platform Information$(NC)"
	@echo "=================================="
	@echo "Services:"
	@echo "  - Customer Facing Service: $(CUSTOMER_SERVICE)/"
	@echo "  - Order Management Service: $(ORDER_SERVICE)/"
	@echo "  - Shared Library: $(SHARED_LIB)/"
	@echo ""
	@echo "Key directories:"
	@echo "  - Specs: specs/"
	@echo "  - Documentation: docs/"
	@echo "  - Manual tests: manual-tests/"
	@echo "  - Infrastructure: infrastructure/"
	@echo ""
	@echo "Coverage reports will be generated in target/site/jacoco-*/ directories"

.PHONY: deps
deps: ## Show dependency tree
	@echo "$(YELLOW)Showing dependency tree...$(NC)"
	$(MAVEN) dependency:tree

.PHONY: outdated
outdated: ## Check for outdated dependencies
	@echo "$(YELLOW)Checking for outdated dependencies...$(NC)"
	$(MAVEN) versions:display-dependency-updates

# CI/CD targets
.PHONY: ci
ci: clean compile test package ## CI pipeline (clean, compile, test, package)
	@echo "$(GREEN)CI pipeline completed successfully.$(NC)"

.PHONY: cd
cd: ci docker-build ## CD pipeline (CI + Docker build)
	@echo "$(GREEN)CD pipeline completed successfully.$(NC)"
