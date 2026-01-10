# AI Orchestrator Testing Guide

This guide covers how to test the AI Orchestrator application, including backend API, frontend UI, and intelligence features.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Starting the Application](#starting-the-application)
3. [Setting Up Test Data](#setting-up-test-data)
4. [Testing Backend API](#testing-backend-api)
5. [Testing Frontend UI](#testing-frontend-ui)
6. [Testing Intelligence Features](#testing-intelligence-features)
7. [Testing Scenarios](#testing-scenarios)

---

## Prerequisites

### 1. Database Setup
- **MySQL** running on `localhost:3306`
- Database: `ai_orchestrator_v5` (will be created automatically)
- Username: `root`
- Password: `root` (or update in `application.yml`)

```sql
-- Create database if needed
CREATE DATABASE IF NOT EXISTS ai_orchestrator_v5;
```

### 2. Environment Variables
```bash
# Required for JWT authentication
export JWT_SECRET="your-secret-key-minimum-32-characters-long"

# Optional: For OpenAI (if using external LLM)
export OPENAI_API_KEY="your-openai-api-key"
```

### 3. Dependencies
- **Java 17+** (JDK 21 recommended)
- **Maven 3.8+**
- **Node.js 18+** and **npm** (for frontend)
- **MySQL 8.0+**

---

## Starting the Application

### Step 1: Start Backend API

```bash
# Navigate to project root
cd E:\AxisPOCinavative-idea

# Build and start backend
mvn clean install
cd ai-orchestrator-api
mvn spring-boot:run

# Or run from root
mvn spring-boot:run -pl ai-orchestrator-api
```

The backend will start on `http://localhost:8080`

**Verify startup:**
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health Check: http://localhost:8080/actuator/health
- API Docs: http://localhost:8080/v3/api-docs

### Step 2: Start Frontend UI

```bash
# Navigate to UI directory
cd ai-orchestrator-ui

# Install dependencies (first time only)
npm install

# Start development server
npm start
```

The frontend will start on `http://localhost:4200`

---

## Setting Up Test Data

### 1. Generate JWT Token for Testing

```bash
# Get authentication token
curl -X POST "http://localhost:8080/api/auth/token?username=testuser&role=USER"

# Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "testuser",
  "role": "USER"
}

# For admin access
curl -X POST "http://localhost:8080/api/auth/token?username=admin&role=ADMIN"
```

### 2. Create Test Scenarios (via Admin Panel or SQL)

**Via SQL:**
```sql
-- Insert test scenario
INSERT INTO ai_scenarios (scenario_code, scenario_name, description, active, execution_type) 
VALUES ('ACCOUNT_BALANCE', 'Account Balance Inquiry', 'Check account balance', true, 'DB_QUERY');

-- Insert fallback layers
INSERT INTO ai_fallback_layers (layer_code, layer_name, layer_type, priority, enabled, confidence_threshold) 
VALUES 
  ('KEYWORD_MATCHER', 'Keyword Matcher', 'KEYWORDS', 1, true, 0.6),
  ('RULE_ENGINE', 'Rule Engine', 'RULES', 2, true, 0.7),
  ('EMBEDDING_SIMILARITY', 'Embedding Similarity', 'EMBEDDING', 3, true, 0.75),
  ('CONVERSATIONAL_HANDLER', 'Conversational Handler', 'CONVERSATIONAL', 5, true, 0.1);

-- Insert keyword patterns
INSERT INTO ai_keyword_patterns (scenario_code, keyword, weight, active) 
VALUES 
  ('ACCOUNT_BALANCE', 'balance', 1.0, true),
  ('ACCOUNT_BALANCE', 'account balance', 1.5, true),
  ('ACCOUNT_BALANCE', 'check balance', 1.5, true);

-- Insert conversational responses
INSERT INTO ai_conversational_responses (intent_type, response_template, active, priority) 
VALUES 
  ('GREETING', 'Hello! How can I help you today?', true, 1),
  ('THANKS', 'You''re welcome! Is there anything else I can help with?', true, 1),
  ('UNKNOWN', 'I''m not sure I understand. Could you please rephrase?', true, 1);

-- Insert system config
INSERT INTO ai_system_config (config_key, config_value, category, editable) 
VALUES 
  ('assistant_name', 'Axis Banking Assistant', 'SYSTEM', true),
  ('org_name', 'Axis Bank', 'SYSTEM', true);
```

---

## Testing Backend API

### 1. Authentication Test

```bash
# Get token
TOKEN=$(curl -s -X POST "http://localhost:8080/api/auth/token?username=testuser&role=USER" | jq -r '.token')

# Validate token
curl -X GET "http://localhost:8080/api/auth/validate" \
  -H "Authorization: Bearer $TOKEN"
```

### 2. Chat API Test (Non-Streaming)

```bash
TOKEN="your-jwt-token-here"

curl -X POST "http://localhost:8080/api/v2/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "What is my account balance?",
    "userId": "testuser",
    "sessionId": "test-session-001"
  }'
```

**Expected Response:**
```json
{
  "sessionId": "test-session-001",
  "message": "...",
  "responseType": "DIRECT",
  "scenario": "ACCOUNT_BALANCE",
  "confidence": 0.85,
  "executionId": "..."
}
```

### 3. Chat API Test (Streaming SSE)

```bash
TOKEN="your-jwt-token-here"

curl -X POST "http://localhost:8080/api/v2/chat/stream" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "What is my account balance?",
    "userId": "testuser",
    "sessionId": "test-session-002"
  }' \
  --no-buffer
```

**Expected SSE Events:**
```
event: start
data: Processing your request...

event: message
data: Checking account balance...

event: done
```

### 4. Admin API Tests

#### Get All Scenarios
```bash
curl -X GET "http://localhost:8080/api/admin/scenarios" \
  -H "Authorization: Bearer $TOKEN"
```

#### Get Fallback Layers
```bash
curl -X GET "http://localhost:8080/api/admin/intelligence/layers" \
  -H "Authorization: Bearer $TOKEN"
```

#### Update Fallback Layer
```bash
curl -X PUT "http://localhost:8080/api/admin/intelligence/layers/1" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "active": true,
    "confidenceThreshold": 0.75,
    "priority": 1
  }'
```

### 5. Health Check

```bash
curl -X GET "http://localhost:8080/actuator/health"

# Response:
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

---

## Testing Frontend UI

### 1. Access the Application

1. Open browser: http://localhost:4200
2. Login with credentials (or use token from `/api/auth/token`)

### 2. Test Chat Interface

1. Navigate to **Chat** page
2. Type a query: `"What is my account balance?"`
3. Observe:
   - Intent detection in action
   - Response streaming (if SSE enabled)
   - Scenario execution
   - Response formatting

### 3. Test Admin Panel

1. Navigate to **Admin Panel** (requires ADMIN role)
2. Test features:
   - **Scenarios**: View/create/edit scenarios
   - **Intelligence Layers**: Configure fallback layers
   - **Entity Patterns**: Manage entity extraction patterns
   - **Embeddings**: Generate/update scenario embeddings
   - **System Config**: Update system settings

### 4. Test Admin Components

- **Intelligence Layers**: Configure fallback architecture
- **Entity Patterns**: Add regex patterns for entity extraction
- **Embeddings**: Generate embeddings for scenarios
- **Context Memory**: View session-based context
- **RBAC**: Configure role-scenario mappings

---

## Testing Intelligence Features

### 1. Test Keyword Matching

```bash
# Query that should match keywords
curl -X POST "http://localhost:8080/api/v2/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "check my account balance",
    "userId": "testuser"
  }'

# Should use KEYWORD_MATCHER layer
```

### 2. Test Rule Engine

```bash
# Create a rule first (via admin or SQL)
# Then test with matching query
curl -X POST "http://localhost:8080/api/v2/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "show balance for account 12345",
    "userId": "testuser"
  }'
```

### 3. Test Embedding Similarity

```bash
# First generate embeddings via admin panel
# Then test with similar query
curl -X POST "http://localhost:8080/api/v2/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "I want to see how much money I have",
    "userId": "testuser"
  }'

# Should use EMBEDDING_SIMILARITY layer
```

### 4. Test Conversational Handler

```bash
# Test greetings
curl -X POST "http://localhost:8080/api/v2/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "Hello",
    "userId": "testuser"
  }'

# Should use CONVERSATIONAL_HANDLER layer
```

### 5. Test Context Memory

```bash
# First query
curl -X POST "http://localhost:8080/api/v2/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "What is balance for account 12345?",
    "userId": "testuser",
    "sessionId": "session-001"
  }'

# Second query (should use context)
curl -X POST "http://localhost:8080/api/v2/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "What about that account?",
    "userId": "testuser",
    "sessionId": "session-001"
  }'

# Should resolve "that account" to account 12345
```

---

## Testing Scenarios

### Scenario 1: End-to-End Chat Flow

1. **Login** → Get JWT token
2. **Send Query** → "What is my account balance?"
3. **Verify Intent Detection** → Should detect ACCOUNT_BALANCE scenario
4. **Verify Parameter Extraction** → Should extract accountId if present
5. **Verify Response** → Should get formatted response

### Scenario 2: Multi-Layer Fallback

1. **Disable ML Layer** → Set `enabled = false` in fallback layer
2. **Send Query** → Should fallback to next layer
3. **Check Logs** → Verify layer sequence in logs

### Scenario 3: Ambiguous Intent

1. **Send Ambiguous Query** → "balance"
2. **Verify Response** → Should ask for clarification
3. **Verify Ambiguity Detection** → Check for `AMBIGUOUS` scenario in response

### Scenario 4: Missing Parameters

1. **Send Query Without Required Params** → "Show balance"
2. **Verify Follow-up** → Should ask for missing parameters
3. **Send Parameter** → "account 12345"
4. **Verify Execution** → Should execute scenario with parameters

### Scenario 5: Context Resolution

1. **First Query** → "Show balance for account 12345"
2. **Second Query** → "What about that account?"
3. **Verify** → Should use account 12345 from context

---

## Testing Tools

### 1. Swagger UI
- URL: http://localhost:8080/swagger-ui.html
- Use "Authorize" button to add Bearer token
- Test endpoints interactively

### 2. Postman
- Import OpenAPI spec from `/v3/api-docs`
- Create environment with token
- Test all endpoints

### 3. cURL Scripts
- Use provided cURL commands
- Create bash scripts for automation

### 4. Frontend DevTools
- Browser console for debugging
- Network tab to inspect API calls
- Application tab to check tokens

---

## Troubleshooting

### Issue: Authentication Fails
**Solution:** 
- Check JWT_SECRET is set
- Verify token is not expired
- Check token format: `Bearer <token>`

### Issue: No Scenarios Found
**Solution:**
- Insert test scenarios in database
- Check `active = true` flag
- Verify cache refresh

### Issue: Intelligence Layers Not Working
**Solution:**
- Check fallback layers are enabled
- Verify layer priority order
- Check confidence thresholds
- Review application logs

### Issue: Frontend Can't Connect to Backend
**Solution:**
- Verify backend is running on port 8080
- Check CORS configuration
- Verify API URL in `environment.ts`

---

## Performance Testing

### Load Test with Apache Bench

```bash
# Simple load test
ab -n 100 -c 10 -H "Authorization: Bearer $TOKEN" \
  -p chat-request.json -T application/json \
  http://localhost:8080/api/v2/chat
```

### Monitor Metrics

- Actuator Metrics: http://localhost:8080/actuator/metrics
- Health: http://localhost:8080/actuator/health
- Thread Dump: http://localhost:8080/actuator/threaddump

---

## Next Steps

1. **Set up Test Scenarios** in database
2. **Configure Intelligence Layers** via admin panel
3. **Generate Embeddings** for scenarios
4. **Test Chat Flow** end-to-end
5. **Monitor Performance** using Actuator
6. **Review Logs** for debugging

For more details, see:
- API Documentation: http://localhost:8080/swagger-ui.html
- Application Logs: Check console output
- Database: Query tables directly for debugging
