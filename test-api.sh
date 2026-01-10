#!/bin/bash

# AI Orchestrator API Testing Script
# Usage: ./test-api.sh

BASE_URL="http://localhost:8080"
TOKEN=""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== AI Orchestrator API Testing Script ===${NC}\n"

# Step 1: Get JWT Token
echo -e "${YELLOW}1. Getting JWT Token...${NC}"
TOKEN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/token?username=testuser&role=USER")
TOKEN=$(echo $TOKEN_RESPONSE | grep -o '"token":"[^"]*' | grep -o '[^"]*$')

if [ -z "$TOKEN" ]; then
    echo -e "${RED}Failed to get token${NC}"
    echo "Response: $TOKEN_RESPONSE"
    exit 1
fi

echo -e "${GREEN}Token obtained: ${TOKEN:0:50}...${NC}\n"

# Step 2: Validate Token
echo -e "${YELLOW}2. Validating Token...${NC}"
curl -s -X GET "$BASE_URL/api/auth/validate" \
  -H "Authorization: Bearer $TOKEN" | jq .
echo ""

# Step 3: Test Health Check
echo -e "${YELLOW}3. Testing Health Check...${NC}"
curl -s -X GET "$BASE_URL/actuator/health" | jq .
echo ""

# Step 4: Test Chat API (Non-Streaming)
echo -e "${YELLOW}4. Testing Chat API (Non-Streaming)...${NC}"
curl -s -X POST "$BASE_URL/api/v2/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "Hello",
    "userId": "testuser",
    "sessionId": "test-session-001"
  }' | jq .
echo ""

# Step 5: Test Chat API (Streaming SSE)
echo -e "${YELLOW}5. Testing Chat API (Streaming SSE)...${NC}"
echo "Starting SSE stream (press Ctrl+C to stop)..."
curl -s -X POST "$BASE_URL/api/v2/chat/stream" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "What is my account balance?",
    "userId": "testuser",
    "sessionId": "test-session-002"
  }' --no-buffer | head -20
echo ""

# Step 6: Get Scenarios
echo -e "${YELLOW}6. Getting Scenarios...${NC}"
curl -s -X GET "$BASE_URL/api/admin/scenarios" \
  -H "Authorization: Bearer $TOKEN" | jq '. | length' | xargs -I {} echo "Found {} scenarios"
echo ""

# Step 7: Get Fallback Layers
echo -e "${YELLOW}7. Getting Fallback Layers...${NC}"
curl -s -X GET "$BASE_URL/api/admin/intelligence/layers" \
  -H "Authorization: Bearer $TOKEN" | jq '. | length' | xargs -I {} echo "Found {} fallback layers"
echo ""

# Step 8: Test with Different Queries
echo -e "${YELLOW}8. Testing Different Query Types...${NC}"

# Greeting
echo -e "${YELLOW}Query: 'Hello'${NC}"
curl -s -X POST "$BASE_URL/api/v2/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "Hello",
    "userId": "testuser",
    "sessionId": "test-session-003"
  }' | jq -r '.message'
echo ""

# Account Balance
echo -e "${YELLOW}Query: 'What is my account balance?'${NC}"
curl -s -X POST "$BASE_URL/api/v2/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "What is my account balance?",
    "userId": "testuser",
    "sessionId": "test-session-004"
  }' | jq -r '.scenario, .message'
echo ""

echo -e "${GREEN}=== Testing Complete ===${NC}"
