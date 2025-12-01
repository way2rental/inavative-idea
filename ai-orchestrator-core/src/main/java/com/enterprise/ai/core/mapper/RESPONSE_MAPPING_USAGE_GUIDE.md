# JsonPathResponseMapper Usage Guide

## Overview

The `JsonPathResponseMapper` and `ResponseMappingService` work together to convert raw database results into AI-friendly structured JSON for response preparation.

This is per **ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md** specification.

---

## Architecture Flow

```
[Raw DB Query Result]
         ↓
[ResponseMappingService] → Loads mappings from ai_response_mappings table
         ↓
[JsonPathResponseMapper] → Applies JSON Path extraction + masking
         ↓
[Structured AI-Ready JSON]
         ↓
[AI Formatter]
         ↓
[Natural Language Response]
         ↓
[SSE Stream to UI]
```

---

## Core Components

### 1. **JsonPathResponseMapper**
- Low-level mapper that applies JSON Path expressions
- Handles masking (ACCOUNT, PAN, AADHAAR, CARD, etc.)
- Supports nested field mapping
- Works with single row or multiple rows

### 2. **ResponseMappingService**
- High-level service that bridges DB configuration with mapper
- Loads mappings from `ai_response_mappings` table
- Caches mappings for performance
- Provides fallback to legacy mapping if no mappings configured

### 3. **QueryExecutor**
- Integrated with `ResponseMappingService`
- Automatically maps DB results before returning to AI formatter

---

## Database Setup

### Step 1: Create Response Mappings in Database

For example, for `TXN_STATUS` scenario:

```sql
INSERT INTO ai_response_mappings (scenario_code, source_field, target_field, json_path, masking_type, display_order, active)
VALUES
  ('TXN_STATUS', 'txn_id', 'txnId', '$.txn_id', 'NONE', 1, true),
  ('TXN_STATUS', 'status_code', 'status', '$.status_code', 'NONE', 2, true),
  ('TXN_STATUS', 'amount', 'amount', '$.amount', 'NONE', 3, true),
  ('TXN_STATUS', 'account_number', 'accountNumber', '$.account_number', 'ACCOUNT', 4, true),
  ('TXN_STATUS', 'created_at', 'timestamp', '$.created_at', 'NONE', 5, true);
```

### Step 2: Configure Scenario in ai_scenarios Table

```sql
INSERT INTO ai_scenarios (scenario_code, execution_type, sql_query, active)
VALUES (
  'TXN_STATUS',
  'DB_QUERY',
  'SELECT txn_id, status_code, amount, account_number, created_at 
   FROM transactions 
   WHERE txn_id = :txnId',
  true
);
```

---

## Example: Raw DB Result → AI-Ready JSON

### Input: Raw DB Query Result
```json
{
  "txn_id": "TXN123456",
  "status_code": "S",
  "amount": 5000.00,
  "account_number": "123456789012",
  "created_at": "2025-11-29T10:30:00"
}
```

### Mappings Applied
| Source Field | Target Field | JSON Path | Masking Type |
|--------------|--------------|-----------|--------------|
| txn_id | txnId | $.txn_id | NONE |
| status_code | status | $.status_code | NONE |
| amount | amount | $.amount | NONE |
| account_number | accountNumber | $.account_number | ACCOUNT |
| created_at | timestamp | $.created_at | NONE |

### Output: AI-Ready Structured JSON
```json
{
  "scenario": "TXN_STATUS",
  "type": "single",
  "data": {
    "txnId": "TXN123456",
    "status": "S",
    "amount": 5000.00,
    "accountNumber": "XXXX-XXXX-9012",
    "timestamp": "2025-11-29T10:30:00"
  }
}
```

✅ **Notice**: 
- Raw column names are mapped to camelCase
- Account number is masked
- Only configured fields are included
- Clean, predictable structure for AI

---

## Usage in Code

### Automatic Usage (Recommended)

When `QueryExecutor` executes a query, it automatically uses `ResponseMappingService`:

```java
// In your scenario configuration, just define the SQL
// QueryExecutor will automatically:
// 1. Execute the query
// 2. Load mappings for the scenario
// 3. Apply JsonPathResponseMapper
// 4. Return AI-ready JSON
```

### Manual Usage

If you need to use the mapper directly:

```java
@Service
@RequiredArgsConstructor
public class CustomService {
    
    private final ResponseMappingService responseMappingService;
    
    public Map<String, Object> processDbResult(String scenarioCode, List<Map<String, Object>> dbResult) {
        // Map DB result to AI-ready JSON
        return responseMappingService.mapDbResultToAiRequest(scenarioCode, dbResult);
    }
    
    public Map<String, Object> buildCompletePayload(String scenarioCode, String userQuery, Object dbResult) {
        // Build complete AI Formatter payload
        return responseMappingService.buildAiFormatterPayload(scenarioCode, userQuery, dbResult);
    }
}
```

### Direct JsonPathResponseMapper Usage (Advanced)

For custom scenarios without database configuration:

```java
@Service
@RequiredArgsConstructor
public class CustomMapperService {
    
    private final JsonPathResponseMapper jsonPathMapper;
    
    public Map<String, Object> mapCustomResult(Object rawData) {
        // Define mappings programmatically
        List<JsonPathResponseMapper.ResponseMappingConfig> mappings = List.of(
            JsonPathResponseMapper.ResponseMappingConfig.builder()
                .sourceField("account_no")
                .targetField("accountNumber")
                .jsonPath("$.account_no")
                .maskingType("ACCOUNT")
                .build(),
            JsonPathResponseMapper.ResponseMappingConfig.builder()
                .sourceField("balance")
                .targetField("balance")
                .jsonPath("$.balance")
                .maskingType("NONE")
                .build()
        );
        
        return jsonPathMapper.mapResponse(rawData, mappings);
    }
}
```

---

## Multiple Rows Handling

When DB query returns multiple rows:

### Input: Multiple Rows
```json
[
  {"txn_id": "TXN001", "amount": 1000, "account_number": "123456789012"},
  {"txn_id": "TXN002", "amount": 2000, "account_number": "987654321098"},
  {"txn_id": "TXN003", "amount": 3000, "account_number": "555555555555"}
]
```

### Output: Mapped Array
```json
{
  "scenario": "TXN_STATUS",
  "type": "multiple",
  "data": [
    {
      "txnId": "TXN001",
      "amount": 1000,
      "accountNumber": "XXXX-XXXX-9012"
    },
    {
      "txnId": "TXN002",
      "amount": 2000,
      "accountNumber": "XXXX-XXXX-1098"
    },
    {
      "txnId": "TXN003",
      "amount": 3000,
      "accountNumber": "XXXX-XXXX-5555"
    }
  ],
  "count": 3,
  "totalRows": 3
}
```

---

## Masking Types

| Masking Type | Example Input | Example Output |
|--------------|---------------|----------------|
| NONE | Any value | Unchanged |
| ACCOUNT | 123456789012 | XXXX-XXXX-9012 |
| PAN | ABCDE1234F | AB******F |
| AADHAAR | 123456789012 | XXXX-XXXX-9012 |
| CARD | 1234567890123456 | XXXX-XXXX-XXXX-3456 |
| EMAIL | user@example.com | u***@example.com |
| PHONE | 9876543210 | XXXXX43210 |

---

## Benefits

✅ **Security**: Automatic masking of sensitive data  
✅ **Consistency**: AI always receives predictable JSON structure  
✅ **Maintainability**: Mappings configured in DB, not hardcoded  
✅ **Performance**: Mappings are cached  
✅ **Flexibility**: Supports complex JSON Path expressions  
✅ **RBI Compliance**: Ensures sensitive data is properly masked  

---

## Troubleshooting

### No Mappings Configured
If no mappings exist for a scenario, the system falls back to legacy mapping:
```
WARN: No response mappings found for scenario: TXN_STATUS, using legacy mapping
```

**Solution**: Add mappings to `ai_response_mappings` table

### Cache Issues
If you update mappings in DB but old mappings are still used:
```java
responseMappingService.evictMappingCache("TXN_STATUS");
```

### JSON Path Not Working
- Ensure JSON Path expression is correct (e.g., `$.field_name`)
- For nested fields, use dot notation: `$.parent.child`
- For array access: `$.items[0].name`

---

## Advanced JSON Path Examples

### Nested Object Mapping
```sql
INSERT INTO ai_response_mappings (scenario_code, source_field, target_field, json_path, masking_type)
VALUES ('ACCOUNT_INFO', 'customer_name', 'customer.name', '$.customer.name', 'NONE');
```

Output:
```json
{
  "customer": {
    "name": "John Doe"
  }
}
```

### Array Element Access
```sql
INSERT INTO ai_response_mappings (scenario_code, source_field, target_field, json_path, masking_type)
VALUES ('TRANSACTION_LIST', 'first_txn', 'latestTransaction', '$.transactions[0]', 'NONE');
```

---

## Integration with AI Formatter

The structured JSON is automatically passed to the AI Formatter with this exact format:

```json
{
  "scenario": "TXN_STATUS",
  "userQuery": "check my transaction status",
  "structuredData": {
    "txnId": "TXN123456",
    "status": "S",
    "amount": 5000.00,
    "accountNumber": "XXXX-XXXX-9012"
  }
}
```

The AI Formatter then generates a natural language response that is streamed via SSE to the UI.

---

## Best Practices

1. **Always define mappings for production scenarios** - Don't rely on fallback
2. **Use appropriate masking types** - Protect sensitive data
3. **Keep field names consistent** - Use camelCase for target fields
4. **Order matters** - Use `display_order` for consistent output
5. **Test with real data** - Verify masking works correctly
6. **Cache awareness** - Remember mappings are cached for performance

---

## See Also

- `ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md` - Full specification
- `JsonPathResponseMapper.java` - Core mapper implementation
- `ResponseMappingService.java` - High-level service
- `QueryExecutor.java` - Integration point
- `MaskingService.java` - Masking implementation

