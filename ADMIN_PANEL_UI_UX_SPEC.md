# Admin Panel UI & UX Specification – Enterprise AI Orchestrator

Author: Mahendra Malviya  
Scope: Admin-side UI for managing AI Orchestrator (intents, scenarios, audit logs, config, etc.)  
Goal: Provide a clean, consistent, table-based admin interface with:
- Pagination everywhere
- Powerful filtering
- Proper sorting
- Well-structured detail modals
- Production-quality usability

This file extends:
- `ENTERPRISE_AI_ORCHESTRATOR_FULL_SYSTEM_SPEC.md`
- `ENTERPRISE_AI_RESPONSE_MAPPING_AND_SSE_SPEC.md`

---

## 1. GLOBAL ADMIN PANEL PRINCIPLES

1. Every admin listing screen MUST:
    - Use **server-side pagination**
    - Display data **in a table** (except dashboards)
    - Provide **multi-field filtering**
    - Support **column sorting** where applicable

2. Admin screens include (at minimum):
    - Intents Master Management (`ai_intents_master`)
    - Scenarios & Executors (`ai_scenarios`)
    - Response Mappings (`ai_response_mappings`)
    - Audit Logs (`ai_audit_logs`)
    - Role-Scenario Mappings (`role_scenario_map`)
    - File Registry / DB mapping
    - Ollama / LLM Config View (optional)

3. For any row (intent, scenario, log, etc.), there MUST be:
    - A **“View Details”** option
    - That opens a **modal drawer / dialog**
    - With clearly structured, readable content

4. UI MUST be:
    - Consistent across all screens
    - Mobile-friendly enough for at least tablet resolution
    - Keyboard-accessible (tab navigation)

---

## 2. GLOBAL TABLE PATTERN (REUSABLE COMPONENT)

Copilot must create a **reusable table component** with these features:

### 2.1 Table Layout

- Columns: auto-fit with min-width
- Sticky header (header remains visible on scroll)
- Row hover highlight
- Alternating row background is optional but preferred

### 2.2 Pagination

- Server-side
- Controls:
    - `Page Size` dropdown: [10, 25, 50, 100]
    - `Previous / Next` buttons
    - Current page indicator: `Page X of Y`
- When user changes filters or sorting:
    - Reset to page 1
- API contract must include:
    - `page`
    - `size`
    - `sortBy`
    - `sortDirection`
    - `filters` object

### 2.3 Sorting

- Clickable column headers for sortable fields
- `↑` / `↓` indicator on active sorted column
- At least the following sortable per screen:
    - Intents: `scenario_code`, `category`, `updated_at`
    - Scenarios: `scenario_code`, `execution_type`, `active`
    - Audit Logs: `request_time`, `user_id`, `scenario_code`, `success`

### 2.4 Filters (Global Pattern)

Each admin table MUST have:
- A filter bar above the table
- Typical filter elements:
    - Free text search input (search by code/name/userId)
    - Dropdowns (scenario, execution type, role, etc.)
    - Date range pickers (for logs)
    - Status toggles (active/inactive/success/failure)

Filters MUST be applied via:
- Pressing Enter OR
- Clicking a `Filter / Apply` button
- There should be a `Clear Filters` button

---

## 3. SCREEN-BY-SCREEN REQUIREMENTS

### 3.1 Intents Master Screen

**Route Example:** `/admin/intents`

**Table Columns:**
- Scenario Code
- Description
- Category
- Required Params (compact view)
- Optional Params (compact view)
- Confidence Threshold
- Active (Yes/No)
- Last Updated
- Actions (View / Edit / Clone / Deactivate)

**Filters:**
- Text search: scenario code or description
- Category dropdown
- Active status (All / Active / Inactive)
- Confidence threshold range (min / max)

**View Details Modal (Important):**
Sections:
1. **Header**
    - Scenario Code
    - Description
    - Active badge

2. **Parameters Section**
    - Required Params: displayed as chips/tags
    - Optional Params: chips/tags

3. **Signals Section**
    - Positive Signals: bullet list or chip list
    - Negative Signals: bullet list

4. **Examples Section**
    - Example phrases: each on separate line

5. **Technical Section**
    - Confidence threshold
    - Follow-up strategy
    - Category
    - Created / updated timestamps

JSON arrays (like required_params) MUST be formatted as:
- Pretty text
- Not raw JSON string

---

### 3.2 Scenarios & Executors Screen

**Route Example:** `/admin/scenarios`

**Table Columns:**
- Scenario Code
- Execution Type (DB_QUERY / HTTP_CALL / VECTOR_SEARCH, etc.)
- Target DB Key / HTTP URL (shortened)
- Active (Yes/No)
- Last Updated
- Actions (View / Edit / Test)

**Filters:**
- Scenario code
- Execution type dropdown
- Active status
- DB key

**View Details Modal:**
Sections:
1. **Overview**
    - Scenario code
    - Description (from `ai_intents_master` if joined)

2. **Execution Configuration**
    - Execution type (DB_QUERY / HTTP_CALL)
    - For DB_QUERY:
        - db_key
        - sql_query (syntax-highlighted, read-only)
    - For HTTP_CALL:
        - method
        - url
        - headers (if any)
        - query params mapping

3. **Mapping Info**
    - Linked response mappings count
    - Linked intent info

4. **Security Summary**
    - Read-only enforced: Yes (hardcoded label)
    - Allowed roles (resolved from `role_scenario_map`)

SQL / JSON must be displayed in a scrollable code block or monospaced section.

---

### 3.3 Audit Logs Screen (Key Screen)

**Route Example:** `/admin/audit-logs`

**Table Columns:**
- Time (request_time)
- User ID
- Scenario Code
- Execution Result (Success / Failed)
- Response Time (duration)
- Actions (View Details)

**Filters:**
- Date range (From / To)
- User ID (text)
- Scenario Code (dropdown + search)
- Success (All / Success / Failure)
- Min / Max response time in ms (optional)

**Sorting:**
- Default sort: request_time DESC (latest first)
- Allow sorting by:
    - user_id
    - scenario_code
    - success
    - response_time

**View Details Modal (Very Important):**
Sections:
1. **Header**
    - Execution ID
    - Status badge (Success / Failed)
    - Timestamp

2. **User Info**
    - user_id
    - roles (if available to show)
    - source (optional: channel/app/web)

3. **Scenario Info**
    - scenario_code
    - resolved scenario description
    - parameters summary

4. **Timing Info**
    - request_time
    - response_time
    - total duration (ms)

5. **Raw Intent JSON**
    - Pretty-printed JSON
    - Collapsible section (`Show/Hide Intent JSON`)

6. **Raw Result JSON**
    - Pretty-printed JSON
    - Collapsible (`Show/Hide Result JSON`)

7. **Error Info** (only when failed)
    - error_message
    - stack trace (if intended to be shown, otherwise short version)

**Formatting Rules:**
- JSON must be pretty-printed with indentation.
- Long values should wrap or be scrollable.
- Sensitive data MUST be masked (same masking rules as backend).

---

### 3.4 Role-Scenario Mapping Screen

**Route Example:** `/admin/rbac`

**Table Columns:**
- Role Name
- Scenario Code
- Scenario Description (optional)
- Actions

**Filters:**
- Role name
- Scenario code

**Modal:**
- Show list of scenarios for selected role
- Allow bulk view-only (no editing unless specified)

---

### 3.5 File Registry & DB Mapping Screen

**Route Example:** `/admin/file-registry`

**Table Columns:**
- File Type
- File Extension
- Target DB Key
- Table Name
- Active (Yes/No)

**Filters:**
- File Type
- Extension
- DB Key

**View Modal:**
- All fields
- Example use cases
- Linked scenarios (e.g., FILE_STATUS, FILE_ERRORS)

---

## 4. INTERNAL “VIEW DETAILS” MODAL – GENERAL DESIGN RULES

For ANY “View Details” modal:

1. Use a **clean layout with sections**:
    - Title + Badge (status)
    - Meta info (IDs, timestamps)
    - Business fields
    - Technical fields
    - Raw JSON/code sections (collapsible)

2. Avoid raw unformatted JSON in base view.
    - Show user-friendly summary first
    - Provide `Expand JSON` or `View Raw` sections

3. Use:
    - Bold labels (`Label:`)
    - Monospace font for IDs, codes, JSON and SQL
    - Spacing between sections

4. Modal MUST be:
    - Scrollable if content is long
    - Closeable via `X` icon + outside click + ESC

---

## 5. RESPONSIVE & UX BEHAVIOR

- On smaller width:
    - Table can scroll horizontally
    - Filters can collapse into a “Filter” drawer

- Any operation (filter, pagination, sorting):
    - Should show a loading state
    - Should not freeze UI

- Error states:
    - Show a friendly error banner above table
    - Do not break layout

---

## 6. REUSABLE COMPONENTS COPILOT MUST BUILD

1. `AdminTable` – Generic table with:
    - Header, body, pagination, sorting, empty state

2. `FilterBar` – Configurable filter row

3. `DetailsModal` – Standard modal with:
    - Title
    - Sections
    - JSON viewer slot

4. `JsonViewerComponent` – Pretty-print JSON with:
    - Collapsible
    - Copy-to-clipboard

5. `StatusBadge` – For:
    - Active / Inactive
    - Success / Failed

---

## 7. NON-FUNCTIONAL REQUIREMENTS

- No inline styles; use consistent styling system (e.g., Angular + Tailwind or SCSS)
- Components should be:
    - Reusable
    - Typed (TypeScript interfaces)
    - Well-named

- Big tables must:
    - Use virtual scroll if rows > 1000 (optional in phase 1)
    - Use loading placeholders/skeletons while fetching

---

## 8. DONE CRITERIA (For Copilot / Dev Team)

An Admin area page is considered **DONE** when:

- It uses the shared AdminTable component
- It supports:
    - Pagination
    - Sorting
    - Filters
- It displays data in a table (except dashboards)
- Clicking a row or “View” opens a structured modal
- Audit log modal shows nicely formatted intent & result JSON
- No raw JSON or ugly dump is shown to the admin by default
- The design is consistent across all Admin screens

---

END OF SPECIFICATION
