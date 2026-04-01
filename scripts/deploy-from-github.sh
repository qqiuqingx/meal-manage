#!/usr/bin/env bash

set -euo pipefail

# Production deployment script:
# 1. Clone or update the target Git repository
# 2. Rebuild Docker images from source
# 3. Restart Docker Compose services

REPO_URL="${REPO_URL:-git@github.com:qqiuqingx/meal-manage.git}"
BRANCH="${BRANCH:-master}"
DEPLOY_BASE_DIR="${DEPLOY_BASE_DIR:-/data/meals/meal-manage}"
ENV_FILE="${ENV_FILE:-/data/meals/.env}"
COMPOSE_FILE_REL="${COMPOSE_FILE_REL:-docker/docker-compose.yml}"

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

# 重试直到成功的辅助函数（用于拉取镜像，网络不稳定时用）
retry_until_success() {
  local max_attempts="${1:-3}"
  local description="$2"
  shift 2
  local attempt=1
  while (( attempt <= max_attempts )); do
    log "[$attempt/$max_attempts] $description"
    if "$@"; then
      return 0
    fi
    log "failed, retrying in 30s..."
    sleep 30
    ((attempt++))
  done
  log "failed after $max_attempts attempts: $description"
  return 1
}

deploy_compose() {
  local compose_file="$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL"

  require_file "$compose_file"
  require_file "$ENV_FILE"

  # 生成镜像 tag，精确到分钟
  export IMAGE_TAG
  IMAGE_TAG=$(date '+%Y%m%d%H%M')

  log "rebuilding and starting containers, image tag: $IMAGE_TAG"
  (
    cd "$DEPLOY_BASE_DIR"
    # 停止所有容器，释放内存（4GB 服务器构建时不能有其他容器跑着）
    docker compose -f "$compose_file" --env-file "$ENV_FILE" down || true

    # 先预拉取所有基础镜像（国内访问 Docker Hub 不稳定，提前拉取减少构建失败）
    log "pre-pulling base images..."
    retry_until_success 3 "pulling node:16" \
      docker pull node:16 || true
    retry_until_success 3 "pulling nginx:1.25-alpine" \
      docker pull nginx:1.25-alpine || true

    # 串行构建，避免并发内存不足；两个镜像都生成后再统一启动
    # 部分 Compose 版本在 up 单个服务时仍会探测其他服务，导致 frontend 镜像未构建时报错
    log "building backend..."
    DOCKER_BUILDKIT=1 docker compose -f "$compose_file" --env-file "$ENV_FILE" build backend

    log "building frontend..."
    DOCKER_BUILDKIT=1 docker compose -f "$compose_file" --env-file "$ENV_FILE" build frontend

    log "starting containers..."
    DOCKER_BUILDKIT=1 docker compose -f "$compose_file" --env-file "$ENV_FILE" up -d --no-build
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
  require_cmd docker

  if ! docker compose version >/dev/null 2>&1; then
    log "docker compose plugin is required"
    exit 1
  fi

  prepare_repo
  deploy_compose
  print_summary
}

main "$@"
