# AI Orchestrator - Production Dockerfile
# Multi-stage build for optimized image size

# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app
COPY pom.xml .
COPY ai-orchestrator-common/pom.xml ai-orchestrator-common/
COPY ai-orchestrator-data/pom.xml ai-orchestrator-data/
COPY ai-orchestrator-core/pom.xml ai-orchestrator-core/
COPY ai-orchestrator-llm/pom.xml ai-orchestrator-llm/
COPY ai-orchestrator-security/pom.xml ai-orchestrator-security/
COPY ai-orchestrator-api/pom.xml ai-orchestrator-api/

# Download dependencies (cached layer)
RUN apt-get update && apt-get install -y maven && \
    mvn dependency:go-offline -B

# Copy source and build
COPY . .
RUN mvn package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy

# Install wget for health checks (curl not available in jre-jammy)
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Add non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/ai-orchestrator-api/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check - uses liveness probe with wget
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/api/public/health/live || exit 1

# JVM options for containers
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
