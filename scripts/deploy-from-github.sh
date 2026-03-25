#!/usr/bin/env bash

set -euo pipefail

# Production deployment script:
# 1. Clone or update the target Git repository
# 2. Build the backend JAR with Maven
# 3. Rebuild and restart Docker Compose services

REPO_URL="${REPO_URL:-git@github.com:qqiuqingx/meal-manage.git}"
BRANCH="${BRANCH:-master}"
DEPLOY_BASE_DIR="${DEPLOY_BASE_DIR:-/data/meals/meal-manage}"
ENV_FILE="${ENV_FILE:-/data/meals/.env}"
COMPOSE_FILE_REL="${COMPOSE_FILE_REL:-docker/docker-compose.yml}"
BACKEND_JAR_REL="${BACKEND_JAR_REL:-eladmin/eladmin-system/target/eladmin-system-1.1.jar}"
BACKEND_JAR_NAME="${BACKEND_JAR_NAME:-eladmin-system-1.1.jar}"

timestamp() {
  date '+%Y-%m-%d %H:%M:%S'
}

log() {
  printf '[%s] %s\n' "$(timestamp)" "$*"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "missing required command: $1"
    exit 1
  fi
}

require_file() {
  if [[ ! -f "$1" ]]; then
    log "required file not found: $1"
    exit 1
  fi
}

prepare_repo() {
  mkdir -p "$(dirname "$DEPLOY_BASE_DIR")"

  if [[ -d "$DEPLOY_BASE_DIR/.git" ]]; then
    log "updating repository in $DEPLOY_BASE_DIR"
    git -C "$DEPLOY_BASE_DIR" fetch origin "$BRANCH"
    git -C "$DEPLOY_BASE_DIR" checkout "$BRANCH"
    git -C "$DEPLOY_BASE_DIR" reset --hard "origin/$BRANCH"
  else
    log "cloning repository $REPO_URL to $DEPLOY_BASE_DIR"
    rm -rf "$DEPLOY_BASE_DIR"
    git clone --branch "$BRANCH" --depth 1 "$REPO_URL" "$DEPLOY_BASE_DIR"
  fi
}

build_backend() {
  log "building backend JAR"
  (
    cd "$DEPLOY_BASE_DIR/eladmin"
    mvn -pl eladmin-system -am clean package -DskipTests
  )

  require_file "$DEPLOY_BASE_DIR/$BACKEND_JAR_REL"
  cp -f "$DEPLOY_BASE_DIR/$BACKEND_JAR_REL" "$DEPLOY_BASE_DIR/$BACKEND_JAR_NAME"
}

deploy_compose() {
  local compose_file="$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL"

  require_file "$compose_file"
  require_file "$ENV_FILE"
  require_file "$DEPLOY_BASE_DIR/$BACKEND_JAR_NAME"

  log "rebuilding and starting containers"
  (
    cd "$DEPLOY_BASE_DIR"
    docker compose -f "$compose_file" --env-file "$ENV_FILE" up -d --build
  )
}

print_summary() {
  log "deployment completed"
  log "repository : $REPO_URL"
  log "branch     : $BRANCH"
  log "deploy dir : $DEPLOY_BASE_DIR"
  log "env file   : $ENV_FILE"
}

main() {
  require_cmd git
  require_cmd mvn
  require_cmd docker

  if ! docker compose version >/dev/null 2>&1; then
    log "docker compose plugin is required"
    exit 1
  fi

  prepare_repo
  build_backend
  deploy_compose
  print_summary
}

main "$@"
