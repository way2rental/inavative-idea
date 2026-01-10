# AI Orchestrator API Testing Script (PowerShell)
# Usage: .\test-api.ps1

$baseUrl = "http://localhost:8080"
$token = ""

Write-Host "=== AI Orchestrator API Testing Script ===" -ForegroundColor Yellow
Write-Host ""

# Step 1: Get JWT Token
Write-Host "1. Getting JWT Token..." -ForegroundColor Yellow
$tokenResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/token?username=testuser&role=USER" -Method POST
$token = $tokenResponse.token

if ([string]::IsNullOrEmpty($token)) {
    Write-Host "Failed to get token" -ForegroundColor Red
    Write-Host "Response: $($tokenResponse | ConvertTo-Json)"
    exit 1
}

Write-Host "Token obtained: $($token.Substring(0, [Math]::Min(50, $token.Length)))..." -ForegroundColor Green
Write-Host ""

# Step 2: Validate Token
Write-Host "2. Validating Token..." -ForegroundColor Yellow
$headers = @{
    "Authorization" = "Bearer $token"
}
$validateResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/validate" -Method GET -Headers $headers
$validateResponse | ConvertTo-Json
Write-Host ""

# Step 3: Test Health Check
Write-Host "3. Testing Health Check..." -ForegroundColor Yellow
$healthResponse = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method GET
$healthResponse | ConvertTo-Json
Write-Host ""

# Step 4: Test Chat API (Non-Streaming)
Write-Host "4. Testing Chat API (Non-Streaming)..." -ForegroundColor Yellow
$chatBody = @{
    query = "Hello"
    userId = "testuser"
    sessionId = "test-session-001"
} | ConvertTo-Json

$chatHeaders = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $token"
}

$chatResponse = Invoke-RestMethod -Uri "$baseUrl/api/v2/chat" -Method POST -Body $chatBody -Headers $chatHeaders
$chatResponse | ConvertTo-Json -Depth 5
Write-Host ""

# Step 5: Get Scenarios
Write-Host "5. Getting Scenarios..." -ForegroundColor Yellow
try {
    $scenariosResponse = Invoke-RestMethod -Uri "$baseUrl/api/admin/scenarios" -Method GET -Headers $headers
    $scenarioCount = if ($scenariosResponse -is [Array]) { $scenariosResponse.Count } else { 0 }
    Write-Host "Found $scenarioCount scenarios" -ForegroundColor Green
} catch {
    Write-Host "Error getting scenarios: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Step 6: Get Fallback Layers
Write-Host "6. Getting Fallback Layers..." -ForegroundColor Yellow
try {
    $layersResponse = Invoke-RestMethod -Uri "$baseUrl/api/admin/intelligence/layers" -Method GET -Headers $headers
    $layerCount = if ($layersResponse -is [Array]) { $layersResponse.Count } else { 0 }
    Write-Host "Found $layerCount fallback layers" -ForegroundColor Green
} catch {
    Write-Host "Error getting layers: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Step 7: Test with Different Queries
Write-Host "7. Testing Different Query Types..." -ForegroundColor Yellow

# Greeting
Write-Host "Query: 'Hello'" -ForegroundColor Yellow
$greetingBody = @{
    query = "Hello"
    userId = "testuser"
    sessionId = "test-session-003"
} | ConvertTo-Json

$greetingResponse = Invoke-RestMethod -Uri "$baseUrl/api/v2/chat" -Method POST -Body $greetingBody -Headers $chatHeaders
Write-Host "Response: $($greetingResponse.message)" -ForegroundColor Cyan
Write-Host ""

# Account Balance
Write-Host "Query: 'What is my account balance?'" -ForegroundColor Yellow
$balanceBody = @{
    query = "What is my account balance?"
    userId = "testuser"
    sessionId = "test-session-004"
} | ConvertTo-Json

$balanceResponse = Invoke-RestMethod -Uri "$baseUrl/api/v2/chat" -Method POST -Body $balanceBody -Headers $chatHeaders
Write-Host "Scenario: $($balanceResponse.scenario)" -ForegroundColor Cyan
Write-Host "Response: $($balanceResponse.message)" -ForegroundColor Cyan
Write-Host ""

Write-Host "=== Testing Complete ===" -ForegroundColor Green
