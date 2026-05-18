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
# 保留最近 N 个版本的 Docker 镜像
KEEP_IMAGE_COUNT="${KEEP_IMAGE_COUNT:-3}"

# 校验 KEEP_IMAGE_COUNT 必须为正整数，且至少为 2（保证回退脚本始终有镜像可选）
if ! [[ "$KEEP_IMAGE_COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] KEEP_IMAGE_COUNT must be a positive integer, got: $KEEP_IMAGE_COUNT" >&2
  exit 1
fi
if (( KEEP_IMAGE_COUNT < 2 )); then
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] KEEP_IMAGE_COUNT must be >= 2 to support rollback (got: $KEEP_IMAGE_COUNT)" >&2
  exit 1
fi

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
  local previous_commit=""

  mkdir -p "$(dirname "$DEPLOY_BASE_DIR")"

  if [[ -d "$DEPLOY_BASE_DIR/.git" ]]; then
    log "updating repository in $DEPLOY_BASE_DIR"
    # 存储上一次提交，在任何 git 操作之前
    previous_commit=$(git -C "$DEPLOY_BASE_DIR" rev-parse HEAD 2>/dev/null || echo "")

    # 检查是否为浅克隆，如果是则获取完整历史
    if git -C "$DEPLOY_BASE_DIR" rev-parse --is-shallow-repository 2>/dev/null | grep -q true; then
      log "repository is shallow, fetching full history for change detection"
      git -C "$DEPLOY_BASE_DIR" fetch --unshallow
    fi

    git -C "$DEPLOY_BASE_DIR" fetch origin "$BRANCH"
    git -C "$DEPLOY_BASE_DIR" checkout "$BRANCH"
    git -C "$DEPLOY_BASE_DIR" reset --hard "origin/$BRANCH"
  else
    log "cloning repository $REPO_URL to $DEPLOY_BASE_DIR"
    rm -rf "$DEPLOY_BASE_DIR"
    git clone --branch "$BRANCH" --depth 1 "$REPO_URL" "$DEPLOY_BASE_DIR"
    # 首次部署 - 没有上一次提交
    previous_commit=""
  fi

  # 返回上一次提交
  echo "$previous_commit"
}

# 检测两个提交之间的变更文件
detect_changes() {
  local previous_commit="$1"
  local current_commit
  current_commit=$(git -C "$DEPLOY_BASE_DIR" rev-parse HEAD)

  # 如果没有上一次提交，强制全量重建
  if [[ -z "$previous_commit" ]]; then
    log "no previous commit found, forcing full rebuild"
    echo "force-full-rebuild"
    return
  fi

  # 验证上一次提交是否存在
  if ! git -C "$DEPLOY_BASE_DIR" cat-file -e "$previous_commit" 2>/dev/null; then
    log "warning: previous commit $previous_commit not found, forcing full rebuild"
    echo "force-full-rebuild"
    return
  fi

  # 获取变更文件列表
  log "comparing commits: $previous_commit -> $current_commit"
  local changed_files
  changed_files=$(git -C "$DEPLOY_BASE_DIR" diff --name-only "$previous_commit" "$current_commit")

  local count
  count=$(echo "$changed_files" | grep -c . || echo "0")
  log "detected $count changed file(s)"

  # 如果没有变更文件，返回空字符串
  if [[ -z "$changed_files" ]]; then
    echo ""
  else
    echo "$changed_files"
  fi
}

check_agent_rule_sync() {
  local previous_commit="$1"
  local current_commit
  current_commit=$(git -C "$DEPLOY_BASE_DIR" rev-parse HEAD)

  if [[ -z "$previous_commit" ]]; then
    return
  fi

  if [[ -x "$DEPLOY_BASE_DIR/scripts/check-agent-rule-sync.sh" ]]; then
    log "checking agent rule registry sync"
    REPO_DIR="$DEPLOY_BASE_DIR" "$DEPLOY_BASE_DIR/scripts/check-agent-rule-sync.sh" "$previous_commit" "$current_commit"
  fi
}

# 根据变更文件确定需要构建的目标
determine_build_targets() {
  local changed_files="$1"

  local rebuild_backend=false
  local rebuild_frontend=false

  # 如果强制全量重建
  if [[ "$changed_files" == "force-full-rebuild" ]]; then
    rebuild_backend=true
    rebuild_frontend=true
  elif [[ -n "$changed_files" ]]; then
    # 检查后端变更
    if echo "$changed_files" | grep -qE "^eladmin/"; then
      rebuild_backend=true
    fi
    if echo "$changed_files" | grep -qE "^docker/mealserver/"; then
      rebuild_backend=true
    fi

    # 检查前端变更
    if echo "$changed_files" | grep -qE "^eladmin-web/"; then
      rebuild_frontend=true
    fi
    if echo "$changed_files" | grep -qE "^docker/mealweb/"; then
      rebuild_frontend=true
    fi

    # 检查共享变更（同时触发后端和前端重建）
    if echo "$changed_files" | grep -qE "^docker/docker-compose\.yml$"; then
      rebuild_backend=true
      rebuild_frontend=true
    fi

    # 保守策略：如果有意外的文件变更，重建所有
    if echo "$changed_files" | grep -qvE "^(eladmin/|eladmin-web/|docker/)"; then
      log "warning: unexpected file change detected, rebuilding both"
      rebuild_backend=true
      rebuild_frontend=true
    fi
  fi

  echo "backend=$rebuild_backend,frontend=$rebuild_frontend"
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
  local previous_commit="$1"
  local compose_file="$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL"

  require_file "$compose_file"
  require_file "$ENV_FILE"

  # 生成镜像 tag，精确到秒（避免同分钟多次部署标签冲突）
  export IMAGE_TAG
  IMAGE_TAG=$(date '+%Y%m%d%H%M%S')

  # 检测并分类变更
  local changed_files
  local build_targets
  changed_files=$(detect_changes "$previous_commit")
  check_agent_rule_sync "$previous_commit"
  build_targets=$(determine_build_targets "$changed_files")

  # 解析构建目标
  local rebuild_backend
  local rebuild_frontend
  rebuild_backend=$(echo "$build_targets" | grep -oP 'backend=\K(true|false)' || echo "false")
  rebuild_frontend=$(echo "$build_targets" | grep -oP 'frontend=\K(true|false)' || echo "false")

  log "change analysis complete:"
  log "  - rebuild backend: $rebuild_backend"
  log "  - rebuild frontend: $rebuild_frontend"

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

    # 根据变更情况条件化构建
    if [[ "$rebuild_backend" == "true" ]]; then
      log "building backend..."
      DOCKER_BUILDKIT=1 docker compose -f "$compose_file" --env-file "$ENV_FILE" build backend
    else
      log "skipping backend build (no changes detected)"
    fi

    if [[ "$rebuild_frontend" == "true" ]]; then
      log "building frontend..."
      DOCKER_BUILDKIT=1 docker compose -f "$compose_file" --env-file "$ENV_FILE" build frontend
    else
      log "skipping frontend build (no changes detected)"
    fi

    # 始终启动容器
    log "starting containers..."
    # 仅在前后端镜像都已存在时跳过构建
    if docker images --format '{{.Repository}}' | grep -qx 'mealserver' && \
       docker images --format '{{.Repository}}' | grep -qx 'mealweb'; then
      DOCKER_BUILDKIT=1 docker compose -f "$compose_file" --env-file "$ENV_FILE" up -d --no-build
    else
      DOCKER_BUILDKIT=1 docker compose -f "$compose_file" --env-file "$ENV_FILE" up -d
    fi
  )
}

# 清理旧版本的 Docker 镜像
cleanup_old_images() {
  log "正在清理旧版本 Docker 镜像（保留最近 $KEEP_IMAGE_COUNT 个版本）..."

  local cleaned_count=0

  # 清理 mealserver 旧镜像
  local old_mealserver_images
  old_mealserver_images=$(docker images --format "{{.Repository}}:{{.Tag}}	{{.ID}}	{{.CreatedAt}}" | \
    grep "^mealserver:" | \
    sort -t $'\t' -k3 -r | \
    tail -n +$((KEEP_IMAGE_COUNT + 1)) || true)

  if [[ -n "$old_mealserver_images" ]]; then
    while IFS=$'\t' read -r image_name image_id image_created; do
      log "  删除旧后端镜像: $image_name (创建时间: $image_created)"
      # 仅忽略"镜像不存在"错误（exit 1），其他错误（如被占用、认证失败）打印警告
      if docker rmi "$image_id" 2>/dev/null; then
        ((cleaned_count++)) || true
      else
        exit_code=$?
        if (( exit_code != 1 )); then
          log "  警告: 无法删除镜像 $image_name，exit $exit_code"
        fi
      fi
    done <<< "$old_mealserver_images"
  fi

  # 清理 mealweb 旧镜像
  local old_mealweb_images
  old_mealweb_images=$(docker images --format "{{.Repository}}:{{.Tag}}	{{.ID}}	{{.CreatedAt}}" | \
    grep "^mealweb:" | \
    sort -t $'\t' -k3 -r | \
    tail -n +$((KEEP_IMAGE_COUNT + 1)) || true)

  if [[ -n "$old_mealweb_images" ]]; then
    while IFS=$'\t' read -r image_name image_id image_created; do
      log "  删除旧前端镜像: $image_name (创建时间: $image_created)"
      if docker rmi "$image_id" 2>/dev/null; then
        ((cleaned_count++)) || true
      else
        exit_code=$?
        if (( exit_code != 1 )); then
          log "  警告: 无法删除镜像 $image_name，exit $exit_code"
        fi
      fi
    done <<< "$old_mealweb_images"
  fi

  log "清理完成: 共删除 $cleaned_count 个旧镜像"
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

  local previous_commit
  previous_commit=$(prepare_repo)
  deploy_compose "$previous_commit"
  cleanup_old_images
  print_summary
}

main "$@"
