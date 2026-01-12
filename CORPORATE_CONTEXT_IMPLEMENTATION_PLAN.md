# Corporate Context Implementation Plan

## Problem Statement

You have **4 separate databases** with **separate tables for different corporates**. The system needs to:
1. Route queries to the correct database based on corporate context
2. Separate corporate data and configurations
3. Support corporate-specific scenarios and knowledge
4. Serve **5 different user types** with different access levels:
   - **CorporateUsers** - Users from specific corporates (need corporate-specific database routing)
   - **MonitoringTeam** - Monitoring/operations team (may need cross-corporate or system-wide access)
   - **AMS Teams** - Application Management Services teams (may need cross-corporate access)
   - **Admin Team** - System administrators (full access to all corporates)
   - **General Users** - Regular users (corporate-specific or general access)

## Current State Analysis

### ✅ What Already Exists:
1. **DataSourceRegistryService** - Supports multiple databases via `dbKey`
2. **RequestContext** - Has `corpCode` field (not populated yet)
3. **AiScenario** - Has `dbKey` field for database routing
4. **JWT Service** - Extracts `orgId` (can be enhanced for `corpCode`)
5. **RBAC System** - Role-based access control via `RoleScenarioMap` table
6. **RbacService** - Role-scenario authorization checks

### ❌ What's Missing:
1. **Corporate Code Extraction** - Not extracted from JWT/request
2. **Corporate-Database Mapping** - No mapping service
3. **Corporate-Aware Routing** - QueryExecutor doesn't use DataSourceRegistryService
4. **Corporate Context Flow** - Not passed through kernel
5. **User Type Definitions** - Need to define 5 user types with proper roles
6. **Role-Based Database Access** - Different roles need different database access patterns
7. **Cross-Corporate Access Control** - MonitoringTeam/AMS Teams may need multi-corporate access

## Solution Architecture

### Phase 1: Corporate Context Extraction

**1.1 Enhance JWT Service**
- Add `extractCorpCode(String token)` method
- Extract `corpCode` claim from JWT

**1.2 Update JWT Authentication Filter**
- Populate `RequestContext.corpCode` from JWT
- Use `orgId` as fallback for `corpCode` if not present

**1.3 Add Corporate Code to ChatRequest (Optional)**
- Can be extracted from RequestContext instead
- Keep request DTO clean (corporate comes from security context)

### Phase 2: Corporate-Database Mapping

**2.1 Create Corporate Database Mapping Table**
```sql
CREATE TABLE ai_corporate_database_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    corporate_code VARCHAR(50) NOT NULL UNIQUE,
    database_key VARCHAR(50) NOT NULL,
    database_name VARCHAR(100),
    description TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_corporate_code (corporate_code),
    INDEX idx_database_key (database_key)
);
```

**2.2 Create Entity and Repository**
- `CorporateDatabaseMapping.java` entity
- `CorporateDatabaseMappingRepository.java` repository

**2.3 Create Corporate Database Service**
- `CorporateDatabaseService.java`
- Methods:
  - `getDatabaseKeyForCorporate(String corpCode)` - Map corporate to database
  - `getCorporateForDatabaseKey(String dbKey)` - Reverse lookup
  - `registerCorporateMapping(String corpCode, String dbKey)` - Admin use

### Phase 3: Corporate-Aware Database Routing

**3.1 Enhance DataSourceRegistryService**
- Already supports multiple databases ✅
- Just needs corporate mapping service integration

**3.2 Fix QueryExecutor**
- **CRITICAL ISSUE**: QueryExecutor uses single `jdbcTemplate`
- **FIX**: Use `DataSourceRegistryService.resolveByDbKey()` instead
- Get `dbKey` from scenario: `scenario.getDbKey()`
- Resolve JdbcTemplate dynamically per query

**3.3 Add Corporate-Aware Database Resolution**
- Create `DatabaseAccessResolver` service
- In QueryExecutor:
  1. Get user type and corporate code from RequestContext
  2. Check user's corporate assignments and access level
  3. Resolve database access based on user type:
     - **CorporateUsers**: Use their assigned corporate database
     - **MonitoringTeam**: Can query any corporate database (read-only)
     - **AMS Teams**: Can query assigned corporate databases
     - **Admin Team**: Can query any database
     - **General Users**: Use corporate mapping or default
  4. If scenario has dbKey, validate user has access to that database
  5. Map corporate code → database key via CorporateDatabaseService
  6. Resolve JdbcTemplate from DataSourceRegistryService

### Phase 4: User Type and Role Management

**4.1 Define User Types and Roles**
- Create role definitions for 5 user types:
  - `CORPORATE_USER` - CorporateUsers (corporate-specific access)
  - `MONITORING_TEAM` - MonitoringTeam (cross-corporate read access)
  - `AMS_TEAM` - AMS Teams (cross-corporate read/write access)
  - `ADMIN` - Admin Team (full access to all corporates)
  - `GENERAL_USER` - General Users (corporate-specific or general)

**4.2 Role-Based Database Access Rules**
- **CorporateUsers**: Access ONLY their corporate database
- **MonitoringTeam**: Read access to ALL corporate databases (monitoring purposes)
- **AMS Teams**: Read/Write access to assigned corporate databases
- **Admin Team**: Full access to ALL databases
- **General Users**: Access based on corporate assignment or general scenarios

**4.3 Create User Type Configuration Table**
```sql
CREATE TABLE ai_user_type_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_type VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(50) NOT NULL,
    can_access_multiple_corporates BOOLEAN DEFAULT FALSE,
    default_database_access VARCHAR(50), -- 'SINGLE', 'MULTI', 'ALL'
    description TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_type (user_type),
    INDEX idx_role_name (role_name)
);
```

**4.4 Create User-Corporate Assignment Table**
```sql
CREATE TABLE ai_user_corporate_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(100) NOT NULL,
    corporate_code VARCHAR(50) NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    access_level VARCHAR(50) DEFAULT 'READ', -- 'READ', 'WRITE', 'FULL'
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    INDEX idx_user_id (user_id),
    INDEX idx_corporate_code (corporate_code),
    INDEX idx_user_type (user_type),
    UNIQUE KEY uk_user_corporate (user_id, corporate_code)
);
```

### Phase 5: Corporate Context in Kernel Flow

**5.1 Pass Corporate Context to Kernel**
- Update `AxisAiKernel.process()` to accept `corpCode` and `userType`
- Update `KernelAdapterService.processWithKernel()` to pass context
- Get `corpCode` and `userType` from RequestContext in adapter

**5.2 Role-Based Database Resolution**
- Create `DatabaseAccessResolver` service
- Resolve database access based on:
  1. User type and role
  2. Corporate assignments
  3. Scenario `dbKey` override
  4. Cross-corporate access rules

**5.3 Corporate-Aware Scenario Filtering**
- Filter scenarios by corporate code (for CorporateUsers)
- Allow cross-corporate scenarios (for MonitoringTeam, AMS Teams, Admin)
- Support corporate-specific scenarios

**5.4 Corporate-Aware Knowledge Base (Optional)**
- Corporate-specific domain documents
- Corporate-specific banking concepts
- Role-based knowledge filtering

## Implementation Details

### Database Mapping Strategy

**Option A: Direct Corporate → Database Mapping**
- Each corporate maps to ONE database
- Simple, clear separation
- Recommended for your 4-database setup

**Option B: Scenario-Level Database Override**
- Scenario `dbKey` overrides corporate mapping
- More flexible, complex
- Use if scenarios can query multiple corporates

**Recommended: Hybrid Approach**
1. Default: Corporate code → Database mapping
2. Override: Scenario `dbKey` takes precedence if present
3. Fallback: Use corporate mapping if scenario has no `dbKey`

### Corporate Code Sources (Priority Order)

1. **JWT Token** (Primary) - `corpCode` claim
2. **RequestContext** (Secondary) - From JWT extraction
3. **orgId Fallback** (Tertiary) - Use `orgId` as `corpCode` if no explicit `corpCode`
4. **Default Corporate** (Last Resort) - System default

### QueryExecutor Enhancement

**Current (WRONG):**
```java
private final NamedParameterJdbcTemplate jdbcTemplate; // Single database!

List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(finalSql, sqlParams);
```

**Fixed (CORRECT):**
```java
private final DataSourceRegistryService dataSourceRegistry;
private final CorporateDatabaseService corporateDatabaseService;

// Resolve database dynamically
String dbKey = scenario.getDbKey();
if (dbKey == null) {
    // Use corporate mapping
    String corpCode = RequestContextHolder.getContext().getCorpCode();
    dbKey = corporateDatabaseService.getDatabaseKeyForCorporate(corpCode);
}
NamedParameterJdbcTemplate jdbcTemplate = dataSourceRegistry.resolveByDbKey(dbKey);
List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(finalSql, sqlParams);
```

## Configuration Example

### Corporate Database Mapping Setup

```sql
-- Corporate 1: Retail Banking
INSERT INTO ai_corporate_database_mapping (corporate_code, database_key, database_name, active)
VALUES ('CORP_RETAIL', 'retail_db', 'Retail Banking Database', TRUE);

-- Corporate 2: Corporate Banking
INSERT INTO ai_corporate_database_mapping (corporate_code, database_key, database_name, active)
VALUES ('CORP_CORPORATE', 'corporate_db', 'Corporate Banking Database', TRUE);

-- Corporate 3: PFMS
INSERT INTO ai_corporate_database_mapping (corporate_code, database_key, database_name, active)
VALUES ('CORP_PFMS', 'pfms_db', 'PFMS Database', TRUE);

-- Corporate 4: UPI
INSERT INTO ai_corporate_database_mapping (corporate_code, database_key, database_name, active)
VALUES ('CORP_UPI', 'upi_db', 'UPI Database', TRUE);
```

### User Type Configuration Setup

```sql
-- Define 5 user types
INSERT INTO ai_user_type_config (user_type, role_name, can_access_multiple_corporates, default_database_access, description, active)
VALUES 
('CORPORATE_USER', 'CORPORATE_USER', FALSE, 'SINGLE', 'Users from specific corporates - access only their corporate database', TRUE),
('MONITORING_TEAM', 'MONITORING_TEAM', TRUE, 'ALL', 'Monitoring/operations team - read access to all corporate databases', TRUE),
('AMS_TEAM', 'AMS_TEAM', TRUE, 'MULTI', 'Application Management Services teams - access to assigned corporate databases', TRUE),
('ADMIN', 'ADMIN', TRUE, 'ALL', 'System administrators - full access to all databases', TRUE),
('GENERAL_USER', 'GENERAL_USER', FALSE, 'SINGLE', 'General users - corporate-specific or general access', TRUE);
```

### User-Corporate Assignment Examples

```sql
-- CorporateUser assigned to Retail Banking
INSERT INTO ai_user_corporate_assignment (user_id, corporate_code, user_type, access_level, active)
VALUES ('user123', 'CORP_RETAIL', 'CORPORATE_USER', 'FULL', TRUE);

-- MonitoringTeam member (can access all corporates)
INSERT INTO ai_user_corporate_assignment (user_id, corporate_code, user_type, access_level, active)
VALUES ('monitor001', 'CORP_RETAIL', 'MONITORING_TEAM', 'READ', TRUE);
INSERT INTO ai_user_corporate_assignment (user_id, corporate_code, user_type, access_level, active)
VALUES ('monitor001', 'CORP_CORPORATE', 'MONITORING_TEAM', 'READ', TRUE);
INSERT INTO ai_user_corporate_assignment (user_id, corporate_code, user_type, access_level, active)
VALUES ('monitor001', 'CORP_PFMS', 'MONITORING_TEAM', 'READ', TRUE);
INSERT INTO ai_user_corporate_assignment (user_id, corporate_code, user_type, access_level, active)
VALUES ('monitor001', 'CORP_UPI', 'MONITORING_TEAM', 'READ', TRUE);

-- AMS Team member assigned to specific corporates
INSERT INTO ai_user_corporate_assignment (user_id, corporate_code, user_type, access_level, active)
VALUES ('ams001', 'CORP_RETAIL', 'AMS_TEAM', 'WRITE', TRUE);
INSERT INTO ai_user_corporate_assignment (user_id, corporate_code, user_type, access_level, active)
VALUES ('ams001', 'CORP_CORPORATE', 'AMS_TEAM', 'WRITE', TRUE);
```

### Application Configuration

```yaml
spring:
  datasource:
    # Primary database (AI Orchestrator)
    url: jdbc:mysql://localhost:3306/ai_orchestrator_v5
    username: root
    password: root
  
  # Corporate databases (registered at startup)
  corporate-databases:
    retail_db:
      url: jdbc:mysql://localhost:3306/retail_banking
      username: retail_user
      password: retail_pass
    corporate_db:
      url: jdbc:mysql://localhost:3306/corporate_banking
      username: corporate_user
      password: corporate_pass
    pfms_db:
      url: jdbc:mysql://localhost:3306/pfms
      username: pfms_user
      password: pfms_pass
    upi_db:
      url: jdbc:mysql://localhost:3306/upi
      username: upi_user
      password: upi_pass
```

## Security Considerations

1. **Corporate Isolation**: CorporateUsers can ONLY query their assigned corporate database
2. **Cross-Corporate Access**: MonitoringTeam/AMS Teams can access multiple corporates (with proper authorization)
3. **Database Key Validation**: Validate user has access to requested database based on role
4. **Role-Based Access Control**: Enforce RBAC at database routing level
5. **Row-Level Security**: Already implemented ✅ (uses userId, orgId, corpCode)
6. **Audit Logging**: Log corporate code, user type, and database accessed for all queries
7. **JWT Validation**: Corporate code and user type MUST come from JWT (not request body)
8. **User Assignment Validation**: Verify user is assigned to corporate before allowing access

## Testing Strategy

1. **Unit Tests**: Corporate mapping service
2. **Integration Tests**: Database routing per corporate
3. **Security Tests**: Cross-corporate access prevention
4. **End-to-End Tests**: Full flow with corporate context

## Migration Path

1. **Step 1**: Add corporate extraction (backward compatible)
2. **Step 2**: Create mapping table and service
3. **Step 3**: Fix QueryExecutor (critical fix)
4. **Step 4**: Register corporate databases
5. **Step 5**: Test with real corporates
6. **Step 6**: Deploy gradually

## User Type Access Matrix

| User Type | Corporate Access | Database Access | Scenarios Access |
|-----------|-----------------|-----------------|------------------|
| **CorporateUsers** | Single corporate only | Their corporate database only | Corporate-specific scenarios |
| **MonitoringTeam** | All corporates (read) | All corporate databases (read-only) | All scenarios (read-only) |
| **AMS Teams** | Assigned corporates | Assigned corporate databases | Assigned corporate scenarios |
| **Admin Team** | All corporates (full) | All databases (full access) | All scenarios (full access) |
| **General Users** | Single corporate or general | Corporate database or default | General scenarios |

## Priority

**CRITICAL**: 
1. Fix QueryExecutor database routing
2. Define user types and roles
3. Implement role-based database access

**HIGH**: 
1. Corporate extraction and mapping
2. User-corporate assignment system
3. Database access resolver

**MEDIUM**: 
1. Corporate context in kernel
2. Cross-corporate access control
3. User type configuration

**LOW**: 
1. Corporate-specific configurations
2. Role-based knowledge filtering

---

**Next Steps**: Should I start implementing this? I recommend starting with:
1. Fix QueryExecutor database routing (CRITICAL)
2. Add corporate code extraction from JWT
3. Create corporate database mapping service
