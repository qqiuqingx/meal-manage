# =============================================================================
# Stage 1: Copy JAR (already built locally, no Maven needed in container)
# =============================================================================
# Note: use eclipse-temurin:8-jre (non-alpine) for better multi-arch support (including Apple Silicon arm64)
FROM eclipse-temurin:8-jre

# Install dumb-init for proper signal handling and wget for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends \
    dumb-init wget \
    && rm -rf /var/lib/apt/lists/*

# The base image already has user 'ubuntu' (UID 1000) — use it directly
WORKDIR /app

# Create file storage directories and set ownership to ubuntu user
RUN mkdir -p /home/eladmin/file /home/eladmin/avatar && \
    chown -R ubuntu:ubuntu /home/eladmin

# Copy JAR from host build output
COPY --chown=ubuntu:ubuntu eladmin/eladmin-system/target/eladmin-system-1.1.jar app.jar

# Copy startup script
COPY --chown=ubuntu:ubuntu docker/backend/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Switch to ubuntu user (non-root)
USER ubuntu

# Expose backend port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8000/ || exit 1

# Use dumb-init as PID 1 to handle signals properly
ENTRYPOINT ["/app/entrypoint.sh"]
