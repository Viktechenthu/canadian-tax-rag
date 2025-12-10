#!/bin/bash

# Simple test script for the Canadian Tax RAG API
# Usage: ./test-rag.sh

BASE_URL="http://localhost:8080/api"

echo "========================================="
echo "Canadian Tax RAG System - Test Client"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Test 1: Health Check
echo "Test 1: Health Check"
response=$(curl -s "$BASE_URL/health")
if [ $? -eq 0 ]; then
    print_success "Server is running"
    echo "$response" | jq '.'
else
    print_error "Server is not responding"
    exit 1
fi
echo ""

# Test 2: Ingest Documents
echo "Test 2: Ingest Documents"
print_info "Ingesting documents from data/tax-documents/..."
response=$(curl -s -X POST "$BASE_URL/ingest")
echo "$response" | jq '.'
echo ""

# Test 3: Ask a Question
echo "Test 3: Ask a Question"
question="What is the TFSA contribution limit?"
print_info "Question: $question"

response=$(curl -s -X POST "$BASE_URL/ask" \
  -H "Content-Type: application/json" \
  -d "{\"question\": \"$question\"}")

echo "$response" | jq '.'
echo ""

# Test 4: Retrieve Documents
echo "Test 4: Retrieve Documents (Debug)"
print_info "Retrieving documents for: TFSA"

response=$(curl -s "$BASE_URL/retrieve?question=TFSA")
echo "$response" | jq '.'
echo ""

# Interactive Mode
echo "========================================="
echo "Interactive Mode"
echo "========================================="
echo "Enter your questions (or 'quit' to exit):"
echo ""

while true; do
    read -p "Question: " user_question

    if [ "$user_question" = "quit" ]; then
        print_info "Goodbye!"
        exit 0
    fi

    if [ -z "$user_question" ]; then
        continue
    fi

    echo ""
    print_info "Asking: $user_question"

    response=$(curl -s -X POST "$BASE_URL/ask" \
      -H "Content-Type: application/json" \
      -d "{\"question\": \"$user_question\"}")

    answer=$(echo "$response" | jq -r '.answer')
    echo ""
    echo "Answer:"
    echo "$answer"
    echo ""
    echo "---"
    echo ""
done