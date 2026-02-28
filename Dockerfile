# Multi-stage build to minimize final image size
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B

# Copy source code and build application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# Copy JAR from builder stage
COPY --from=builder /build/target/UDAV-1.0.jar app.jar

# Create directories for input data
RUN mkdir -p /app/data/input && \
    chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Environment variables with defaults
ENV JAVA_OPTS="-Xmx1024m -Xms512m"

# Database Configuration
ENV DB_URL="jdbc:postgresql://postgres:5432/udav"
ENV DB_USER="postgres"
ENV DB_PASS="postgres"
ENV DB_SCHEMA="public"
ENV DB_BATCH_SIZE="5000"
ENV DB_MAX_IDENT="255"
ENV DB_DIALECT="POSTGRES"

# DUUI Importer Configuration
ENV DUUI_IMPORTER="false"
ENV DUUI_IMPORTER_PATH="/app/data/input"
ENV DUUI_IMPORTER_WORKERS="4"
ENV DUUI_IMPORTER_CAS_POOL_SIZE="8"

# Application Configuration
ENV APP_INPUT_DIR="/app/data/input"
ENV SROUCE_BUILDER="false"
ENV PIPELINE_IMPORTER="true"
ENV PIPELINE_IMPORTER_FOLDER="/app/pipelines"

# LLM Configuration (optional, set as needed)
ENV LLM_BASE_URL=""
ENV LLM_API_TOKEN=""

# Run the application
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

