#!/bin/bash

# Complete Startup Script for Canadian Tax RAG with Chroma DB

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Canadian Tax RAG - System Startup${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Function to check if a service is running
check_service() {
    local url=$1
    local name=$2

    if curl -s -f -o /dev/null "$url"; then
        echo -e "${GREEN}✓ $name is running${NC}"
        return 0
    else
        echo -e "${RED}✗ $name is not running${NC}"
        return 1
    fi
}

# Step 1: Check if Chroma DB is running
echo -e "${YELLOW}Step 1: Checking Chroma DB...${NC}"
if check_service "http://localhost:8000/api/v1/heartbeat" "Chroma DB"; then
    echo ""
else
    echo -e "${YELLOW}Starting Chroma DB with Docker...${NC}"

    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Docker is not installed!${NC}"
        echo "Please install Docker Desktop from: https://www.docker.com/products/docker-desktop"
        exit 1
    fi

    if [ ! -f "docker-compose.yml" ]; then
        echo -e "${RED}docker-compose.yml not found!${NC}"
        echo "Please create docker-compose.yml from the provided artifact"
        exit 1
    fi

    docker-compose up -d

    echo "Waiting for Chroma DB to start..."
    sleep 5

    if check_service "http://localhost:8000/api/v1/heartbeat" "Chroma DB"; then
        echo ""
    else
        echo -e "${RED}Failed to start Chroma DB${NC}"
        echo "Check logs with: docker logs chroma-db"
        exit 1
    fi
fi

# Step 2: Check Ollama
echo -e "${YELLOW}Step 2: Checking Ollama...${NC}"
if check_service "http://localhost:11434/api/tags" "Ollama"; then
    echo ""
else
    echo -e "${RED}Ollama is not running!${NC}"
    echo "Please start Ollama:"
    echo "  macOS/Linux: ollama serve"
    echo "  Windows: Ollama runs automatically"
    exit 1
fi

# Step 3: Check Ollama models
echo -e "${YELLOW}Step 3: Checking Ollama models...${NC}"

if ollama list | grep -q "llama3.2"; then
    echo -e "${GREEN}✓ llama3.2 model found${NC}"
else
    echo -e "${YELLOW}Downloading llama3.2 model...${NC}"
    ollama pull llama3.2
fi

if ollama list | grep -q "nomic-embed-text"; then
    echo -e "${GREEN}✓ nomic-embed-text model found${NC}"
else
    echo -e "${YELLOW}Downloading nomic-embed-text model...${NC}"
    ollama pull nomic-embed-text
fi

echo ""

# Step 4: Check data directory
echo -e "${YELLOW}Step 4: Checking data directory...${NC}"
if [ -d "data/tax-documents" ]; then
    doc_count=$(find data/tax-documents -type f \( -name "*.pdf" -o -name "*.txt" \) | wc -l)
    echo -e "${GREEN}✓ Data directory exists${NC}"
    echo -e "  Found ${doc_count} documents"
else
    echo -e "${YELLOW}Creating data directory...${NC}"
    mkdir -p data/tax-documents
    echo -e "${YELLOW}⚠ No documents found in data/tax-documents/${NC}"
    echo "  Please add PDF or TXT files to: data/tax-documents/"
fi

echo ""

# Step 5: Build and start Spring Boot app
echo -e "${YELLOW}Step 5: Starting Spring Boot application...${NC}"

if [ ! -f "pom.xml" ]; then
    echo -e "${RED}pom.xml not found! Are you in the project root?${NC}"
    exit 1
fi

echo "Building project..."
mvn clean package -DskipTests

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}All services are ready!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "Starting application..."
echo "Access the application at: http://localhost:8080"
echo ""
echo "API Endpoints:"
echo "  POST http://localhost:8080/api/ingest    - Ingest documents"
echo "  POST http://localhost:8080/api/ask       - Ask questions"
echo "  GET  http://localhost:8080/api/health    - Health check"
echo ""
echo "Press Ctrl+C to stop"
echo ""

mvn spring-boot:run