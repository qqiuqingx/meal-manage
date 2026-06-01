# syntax=docker/dockerfile:1.7

# =============================================================================
# Runtime image
# =============================================================================
FROM eclipse-temurin:8-jre

# Install dumb-init for proper signal handling and wget for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends \
    dumb-init wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Create runtime directories and grant the non-root user write access
RUN mkdir -p /home/eladmin/file /home/eladmin/avatar /app/logs && \
    chown -R ubuntu:ubuntu /home/eladmin /app

# Maven build runs in deploy-from-github.sh with a host-mounted .m2 repository.
COPY --chown=ubuntu:ubuntu .deploy/mealserver/app.jar /app/app.jar
COPY --chown=ubuntu:ubuntu .deploy/mealserver/lib /app/lib

# Copy startup script
COPY --chown=ubuntu:ubuntu docker/mealserver/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

USER ubuntu

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8000/ || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
