.PHONY: all help install-deps check-java install-java check-docker install-docker setup build test test-coverage run clean docker-up docker-down deploy logs

GREEN := \033[0;32m
YELLOW := \033[0;33m
RED := \033[0;31m
NC := \033[0m

APP_NAME := calendar-enrichment-service
DOCKER_IMAGE := $(APP_NAME):latest
OS := $(shell uname -s)

help:
	@echo "$(GREEN)Available commands:$(NC)"
	@echo ""
	@echo "  $(YELLOW)Main:$(NC)"
	@echo "    make all            - Full run: check dependencies + build + run"
	@echo "    make install-deps   - Install all required dependencies (Java, Docker)"
	@echo ""
	@echo "  $(YELLOW)Development:$(NC)"
	@echo "    make setup          - Start infrastructure (PostgreSQL)"
	@echo "    make build          - Build project"
	@echo "    make test           - Run all tests"
	@echo "    make test-coverage  - Run tests with coverage report"
	@echo "    make run            - Run application locally"
	@echo "    make clean          - Clean build artifacts"
	@echo ""
	@echo "  $(YELLOW)Docker:$(NC)"
	@echo "    make docker-up      - Start all containers"
	@echo "    make docker-down    - Stop all containers"
	@echo "    make deploy         - Full deployment with success log"
	@echo "    make logs           - Show container logs"

all: install-deps setup build run

install-deps: check-java check-docker
	@echo "$(GREEN)All dependencies installed!$(NC)"

check-java:
	@echo "$(YELLOW)Checking Java...$(NC)"
	@if command -v java >/dev/null 2>&1; then \
		JAVA_VERSION=$$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1); \
		if [ "$$JAVA_VERSION" -ge 17 ] 2>/dev/null; then \
			echo "$(GREEN)Java $$JAVA_VERSION found ✓$(NC)"; \
		else \
			echo "$(RED)Java version $$JAVA_VERSION is too old. Java 17+ required$(NC)"; \
			$(MAKE) install-java; \
		fi \
	else \
		echo "$(RED)Java not found$(NC)"; \
		$(MAKE) install-java; \
	fi

install-java:
	@echo "$(YELLOW)Installing Java 17...$(NC)"
ifeq ($(OS),Darwin)
	@if command -v brew >/dev/null 2>&1; then \
		echo "$(YELLOW)Installing via Homebrew...$(NC)"; \
		brew install openjdk@17; \
		sudo ln -sfn $$(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk; \
		echo "$(GREEN)Java 17 installed!$(NC)"; \
	else \
		echo "$(RED)Homebrew not found. Install Homebrew (https://brew.sh) or install Java manually$(NC)"; \
		exit 1; \
	fi
else ifeq ($(OS),Linux)
	@if command -v apt-get >/dev/null 2>&1; then \
		echo "$(YELLOW)Installing via apt...$(NC)"; \
		sudo apt-get update && sudo apt-get install -y openjdk-17-jdk; \
		echo "$(GREEN)Java 17 installed!$(NC)"; \
	elif command -v yum >/dev/null 2>&1; then \
		echo "$(YELLOW)Installing via yum...$(NC)"; \
		sudo yum install -y java-17-openjdk-devel; \
		echo "$(GREEN)Java 17 installed!$(NC)"; \
	else \
		echo "$(RED)Package manager not found. Install Java manually$(NC)"; \
		exit 1; \
	fi
else
	@echo "$(RED)Unsupported OS. Install Java 17 manually from https://adoptium.net/$(NC)"
	@exit 1
endif

check-docker:
	@echo "$(YELLOW)Checking Docker...$(NC)"
	@if command -v docker >/dev/null 2>&1; then \
		if docker info >/dev/null 2>&1; then \
			echo "$(GREEN)Docker found and running ✓$(NC)"; \
		else \
			echo "$(YELLOW)Docker installed but not running.$(NC)"; \
			echo "$(YELLOW)Attempting to start Docker Desktop...$(NC)"; \
			$(MAKE) start-docker || (echo "$(YELLOW)Failed to start Docker automatically.$(NC)" && echo "$(YELLOW)Please start Docker Desktop manually and retry.$(NC)" && exit 1); \
		fi \
	else \
		echo "$(RED)Docker not found$(NC)"; \
		$(MAKE) install-docker; \
	fi

install-docker:
	@echo "$(YELLOW)Installing Docker...$(NC)"
ifeq ($(OS),Darwin)
	@if command -v brew >/dev/null 2>&1; then \
		echo "$(YELLOW)Installing Docker Desktop via Homebrew...$(NC)"; \
		brew install --cask docker; \
		echo "$(GREEN)Docker Desktop installed!$(NC)"; \
		echo "$(YELLOW)Starting Docker Desktop... Please wait...$(NC)"; \
		open -a Docker; \
		echo "$(YELLOW)Waiting for Docker to start (this may take a minute)...$(NC)"; \
		until docker info >/dev/null 2>&1; do sleep 2; done; \
		echo "$(GREEN)Docker started!$(NC)"; \
	else \
		echo "$(RED)Homebrew not found. Install Homebrew (https://brew.sh) or download Docker Desktop from https://www.docker.com/products/docker-desktop$(NC)"; \
		exit 1; \
	fi
else ifeq ($(OS),Linux)
	@echo "$(YELLOW)Installing Docker for Linux...$(NC)"
	@curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
	@sudo sh /tmp/get-docker.sh
	@sudo usermod -aG docker $$USER
	@sudo systemctl start docker
	@sudo systemctl enable docker
	@echo "$(GREEN)Docker installed! You may need to re-login.$(NC)"
else
	@echo "$(RED)Unsupported OS. Install Docker manually from https://www.docker.com/$(NC)"
	@exit 1
endif

start-docker:
ifeq ($(OS),Darwin)
	@if pgrep -x "Docker" > /dev/null; then \
		echo "$(YELLOW)Docker Desktop already running, waiting for readiness...$(NC)"; \
	else \
		echo "$(YELLOW)Starting Docker Desktop...$(NC)"; \
		open -a Docker 2>/dev/null || open -a "Docker Desktop" 2>/dev/null || (echo "$(RED)Failed to start Docker Desktop$(NC)" && exit 1); \
	fi
	@echo "$(YELLOW)Waiting for Docker readiness (may take up to 60 seconds)...$(NC)"
	@timeout=60; \
	while [ $$timeout -gt 0 ]; do \
		if docker info >/dev/null 2>&1; then \
			echo "$(GREEN)Docker ready!$(NC)"; \
			exit 0; \
		fi; \
		sleep 2; \
		timeout=$$((timeout - 2)); \
	done; \
	echo "$(RED)Timeout waiting for Docker. Start Docker Desktop manually.$(NC)"; \
	exit 1
else ifeq ($(OS),Linux)
	@sudo systemctl start docker
	@echo "$(GREEN)Docker started!$(NC)"
endif

setup:
	@echo "$(GREEN)Starting infrastructure...$(NC)"
	@docker compose --profile dev up -d postgres
	@echo "$(YELLOW)Waiting for PostgreSQL readiness...$(NC)"
	@until docker compose --profile dev exec -T postgres pg_isready > /dev/null 2>&1; do \
		echo "Waiting for PostgreSQL..."; \
		sleep 2; \
	done
	@echo "$(GREEN)PostgreSQL ready!$(NC)"

build:
	@echo "$(GREEN)Building project...$(NC)"
	@./mvnw clean package -DskipTests
	@echo "$(GREEN)Build completed!$(NC)"

test:
	@echo "$(GREEN)Running tests...$(NC)"
	@./mvnw test
	@echo "$(GREEN)✓ Tests passed successfully!$(NC)"

test-coverage:
	@echo "$(GREEN)Running tests with coverage analysis...$(NC)"
	@./mvnw clean test
	@echo "$(GREEN)✓ Tests completed! Coverage report generated.$(NC)"
	@echo "$(YELLOW)Open report: target/site/jacoco/index.html$(NC)"
ifeq ($(OS),Darwin)
	@open target/site/jacoco/index.html 2>/dev/null || true
else ifeq ($(OS),Linux)
	@xdg-open target/site/jacoco/index.html 2>/dev/null || true
endif

run:
	@echo "$(GREEN)Starting application...$(NC)"
	@echo "$(GREEN)Swagger UI will be available at: http://localhost:8080/swagger-ui/index.html$(NC)"
	@./mvnw spring-boot:run

clean:
	@echo "$(GREEN)Cleaning...$(NC)"
	@./mvnw clean

docker-up:
	@echo "$(GREEN)Building Docker image...$(NC)"
	@docker build -t $(DOCKER_IMAGE) .
	@echo "$(GREEN)Starting all containers...$(NC)"
	@docker compose --profile dev up -d

docker-down:
	@echo "$(GREEN)Stopping containers...$(NC)"
	@docker compose --profile dev down

deploy: docker-up
	@echo ""
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)✓ DEPLOYMENT COMPLETED SUCCESSFULLY!$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo ""
	@echo "$(YELLOW)Application available at:$(NC)"
	@echo "  - Swagger UI: http://localhost:8080/swagger-ui/index.html"
	@echo "  - API Docs:   http://localhost:8080/v3/api-docs"
	@echo ""
	@echo "$(YELLOW)Container status:$(NC)"
	@docker compose --profile dev ps

logs:
	@docker compose --profile dev logs -f

swagger:
	@echo "$(GREEN)Opening Swagger UI...$(NC)"
	@open http://localhost:8080/swagger-ui/index.html 2>/dev/null || xdg-open http://localhost:8080/swagger-ui/index.html 2>/dev/null || echo "$(YELLOW)Open http://localhost:8080/swagger-ui/index.html in browser$(NC)"
