# syntax=docker/dockerfile:1.7

# =============================================================================
# Stage 1: Build backend JAR with Maven
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY docker/mealserver/settings.xml /root/.m2/settings.xml

# Copy pom files first to maximize Docker layer cache reuse
COPY eladmin/pom.xml /build/eladmin/pom.xml
COPY eladmin/eladmin-common/pom.xml /build/eladmin/eladmin-common/pom.xml
COPY eladmin/eladmin-logging/pom.xml /build/eladmin/eladmin-logging/pom.xml
COPY eladmin/eladmin-system/pom.xml /build/eladmin/eladmin-system/pom.xml
COPY eladmin/eladmin-tools/pom.xml /build/eladmin/eladmin-tools/pom.xml
COPY eladmin/eladmin-generator/pom.xml /build/eladmin/eladmin-generator/pom.xml

WORKDIR /build/eladmin
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -s /root/.m2/settings.xml -pl eladmin-system -am dependency:go-offline -DskipTests

# Copy source code after dependencies are cached
WORKDIR /build
COPY eladmin /build/eladmin

WORKDIR /build/eladmin
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -s /root/.m2/settings.xml -pl eladmin-system -am clean package -DskipTests

# =============================================================================
# Stage 2: Runtime image
# =============================================================================
FROM eclipse-temurin:17-jre

# Install dumb-init for proper signal handling and wget for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends \
    dumb-init wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Create runtime user and grant write access
RUN groupadd -r eladmin && useradd -r -g eladmin -d /home/eladmin -m eladmin && \
    mkdir -p /home/eladmin/file /home/eladmin/avatar /app/logs && \
    chown -R eladmin:eladmin /home/eladmin /app

# Copy built JAR from the builder stage
COPY --from=builder --chown=eladmin:eladmin /build/eladmin/eladmin-system/target/eladmin-system-1.1.jar /app/app.jar

# Copy startup script
COPY --chown=eladmin:eladmin docker/mealserver/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

USER eladmin

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8000/ || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
