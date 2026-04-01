# =============================================================================
# Stage 1: Build Vue frontend
# =============================================================================
FROM node:16 AS builder

WORKDIR /app

# Copy package files for Docker layer caching
COPY eladmin-web/package.json eladmin-web/package-lock.json* ./

# Install dependencies - use npm ci for speed, with fallback
# Use Taobao mirror for faster installation in China
RUN npm config set registry https://registry.npmmirror.com && \
    npm ci --legacy-peer-deps || npm install --legacy-peer-deps --no-audit --prefer-offline

# Copy source code
COPY eladmin-web/ ./

# Override VUE_APP_BASE_API: use relative path so Nginx proxies /api/ to backend
ARG VUE_APP_BASE_API=/
ENV VUE_APP_BASE_API=${VUE_APP_BASE_API}

# Remove NODE_OPTIONS=--openssl-legacy-provider from scripts
# Node 16 uses OpenSSL 1.1.1, no legacy provider needed
RUN sed -i 's/NODE_OPTIONS=--openssl-legacy-provider //g' package.json

# Build for production
# 4GB server: limit Node.js heap to 2GB to avoid OOM in Docker container
RUN node_modules/.bin/vue-cli-service build --max-old-space-size=2048

# =============================================================================
# Stage 2: Serve with Nginx
# =============================================================================
FROM nginx:1.25-alpine

# Remove default Nginx config
RUN rm -rf /etc/nginx/conf.d/default.conf /usr/share/nginx/html/*

# Copy built static files from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration
COPY docker/mealweb/nginx.conf /etc/nginx/conf.d/default.conf

# Create nginx user with limited privileges
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://localhost/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
