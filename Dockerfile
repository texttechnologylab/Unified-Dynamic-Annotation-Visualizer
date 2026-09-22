# Multi-stage build to minimize final image size
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy pom.xml and download dependencies into a cached layer.
# dependency:resolve is used instead of dependency:go-offline because go-offline ignores
# <exclusions> and would still try to fetch the excluded Utilities artifact.
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn -B dependency:resolve dependency:resolve-plugins

# Copy source code and build application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Headless Chromium for the batch export API (/api/batch/...). Node.js is needed by the
# Playwright driver: its bundled Node binary is linked against glibc and does not run on
# Alpine/musl, so the driver is pointed at the distribution's Node (PLAYWRIGHT_NODEJS_PATH).
RUN apk add --no-cache chromium nodejs font-dejavu

# Create non-root user for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# Copy JAR from builder stage. --chown here instead of a later "chown -R /app": a chown after
# COPY rewrites the whole 1.2 GB jar into a second image layer.
COPY --from=builder --chown=appuser:appuser /build/target/UDAV-1.0.jar app.jar

# Bundled pipelines and JSON sources (the demos and the evaluation workload). The importers read
# them from disk, not from the classpath, so the container needs its own copy. Point
# PIPELINE_IMPORTER_FOLDER / JSON_IMPORTER_FOLDER at a mounted folder, or set
# PIPELINE_IMPORTER=false, to use your own.
COPY src/main/resources/pipelines /app/pipelines
COPY src/main/resources/sourcefilesJSON /app/sourcefilesJSON

# Create directories for input data
RUN mkdir -p /app/data/input && \
    chown -R appuser:appuser /app/data

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

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
ENV JSON_IMPORTER_FOLDER="/app/sourcefilesJSON"

# LLM Configuration (optional, set as needed)
ENV LLM_BASE_URL=""
ENV LLM_API_TOKEN=""

# Batch export API (headless browser shipped in this image)
ENV BROWSER_EXECUTABLE_PATH="/usr/lib/chromium/chromium"
ENV PLAYWRIGHT_NODEJS_PATH="/usr/bin/node"
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD="1"
# Chromium's sandbox needs unprivileged user namespaces, which Docker's default seccomp
# profile blocks, so with the sandbox on the browser cannot start inside the container.
# Disabling it is a security trade-off (see README). To keep the sandbox, set
# EXPORT_NO_SANDBOX=false and run the container with a seccomp profile that allows
# user namespaces.
ENV EXPORT_NO_SANDBOX="true"

# Run the application
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

