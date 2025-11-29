# Enterprise Scenario-Driven AI Orchestrator

A **generic, reusable AI orchestration platform** that uses **Ollama** as the LLM runtime (self-hosted, offline) and **Spring Boot** as the main orchestrator. The platform connects to live systems (MySQL, batch/file processing, services) in a secure, controlled way and provides natural language access to runtime data through scenarios.

## Architecture

This is a **multimodule Maven project** designed for separation of concerns and easy conversion to separate runnable components.

### Project Structure

```
ai-orchestrator-parent/
├── ai-orchestrator-common/    # Common DTOs, interfaces, utilities
├── ai-orchestrator-core/      # Core scenario engine, router, executor framework
├── ai-orchestrator-llm/       # Ollama LLM client integration
├── ai-orchestrator-security/  # JWT authentication, RBAC
├── ai-orchestrator-data/      # Data layer, repositories, caching
├── ai-orchestrator-api/       # REST API controllers, Spring Boot main app
└── ai-orchestrator-ui/        # Angular 18 frontend
```

### Module Dependencies

```
common
   ↑
   ├── core
   │     ↑
   │     └── api
   ├── llm ────→ api
   ├── security ─→ api
   └── data ─────→ api
```

## Technology Stack

### Backend
- **Java 17+**
- **Spring Boot 3.x**
- **Spring WebFlux** for reactive HTTP client
- **Spring Security** with JWT
- **Spring Data JPA** with MySQL
- **Redis** for distributed caching
- **Caffeine** for local caching
- **Resilience4j** for circuit breaker patterns

### AI Runtime
- **Ollama** - Self-hosted, offline LLM runtime
- Models: `llama3:8b` (configurable)

### Frontend
- **Angular 18**
- Standalone components
- Reactive forms
- HTTP client with interceptors

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Node.js 18+ and npm
- MySQL 8.0+
- Redis (optional)
- Ollama (for LLM features)

### Backend Setup

1. **Build the project:**
   ```bash
   mvn clean install -DskipTests
   ```

2. **Configure the database:**
   
   Update `ai-orchestrator-api/src/main/resources/application.yml`:
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/ai_orchestrator
       username: your_username
       password: your_password
   ```

3. **Configure Ollama:**
   ```yaml
   ollama:
     base-url: http://localhost:11434
     model: llama3:8b
   ```

4. **Run the application:**
   ```bash
   cd ai-orchestrator-api
   mvn spring-boot:run
   ```

   The API will be available at `http://localhost:8080`

### Frontend Setup

1. **Install dependencies:**
   ```bash
   cd ai-orchestrator-ui
   npm install
   ```

2. **Run development server:**
   ```bash
   npm start
   ```

   The UI will be available at `http://localhost:4200`

## API Endpoints

### Authentication
- `POST /api/auth/token` - Generate JWT token
- `GET /api/auth/validate` - Validate JWT token

### Chat
- `POST /api/chat` - Send a chat message
- `POST /api/chat/stream` - Send a chat message with SSE streaming

### Public
- `GET /api/public/health` - Health check
- `GET /api/public/info` - Service information

### Documentation
- `GET /swagger-ui.html` - Swagger UI
- `GET /v3/api-docs` - OpenAPI specification

## Available Scenarios

1. **TXN_STATUS** - Check transaction status by ID
2. **FILE_STATUS** - Check file processing status
3. **ACCOUNT_SUMMARY** - Show account summary

## Security

- JWT-based authentication
- Role-based access control (RBAC)
- Data masking for sensitive information
- Ollama isolation (no direct database access)

## Configuration

Key configuration properties in `application.yml`:

| Property | Description | Default |
|----------|-------------|---------|
| `ollama.base-url` | Ollama API URL | `http://localhost:11434` |
| `ollama.model` | LLM model name | `llama3:8b` |
| `ollama.timeout-seconds` | Request timeout | `60` |
| `jwt.secret` | JWT signing key | (required) |
| `jwt.expiration` | Token expiration (ms) | `86400000` |

## Adding New Scenarios

1. Create a new executor implementing `ScenarioExecutor`:
   ```java
   @Service
   public class MyNewExecutor implements ScenarioExecutor {
       @Override
       public String getScenarioCode() {
           return "MY_NEW_SCENARIO";
       }
       
       @Override
       public ScenarioResult execute(ScenarioRequest request) {
           // Implementation
       }
   }
   ```

2. Register the scenario in the database (optional)

3. Add role mappings for access control

4. Update intent detection prompts in `PromptTemplates`

## License

This project is proprietary software.
