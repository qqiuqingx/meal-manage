#!/usr/bin/env bash

set -euo pipefail

# Production backend deployment script:
# 1. Clone or update the target Git repository
# 2. Build and restart backend only when backend sources changed
# 3. Leave frontend releases to deploy-frontend-local.sh

REPO_URL="${REPO_URL:-git@github.com:qqiuqingx/meal-manage.git}"
BRANCH="${BRANCH:-master}"
DEPLOY_BASE_DIR="${DEPLOY_BASE_DIR:-/data/meals/meal-manage}"
ENV_FILE="${ENV_FILE:-/data/meals/.env}"
COMPOSE_FILE_REL="${COMPOSE_FILE_REL:-docker/docker-compose.yml}"
# 保留最近 N 个版本的 Docker 镜像
KEEP_IMAGE_COUNT="${KEEP_IMAGE_COUNT:-3}"
# Docker 构建前要求的最小可用磁盘空间（MB）
MIN_FREE_DISK_MB="${MIN_FREE_DISK_MB:-4096}"
# Docker 构建缓存清理策略：auto / always / never
DOCKER_BUILD_CACHE_PRUNE="${DOCKER_BUILD_CACHE_PRUNE:-auto}"
DOCKER_DISK_PATH="${DOCKER_DISK_PATH:-/var/lib/docker}"
DEPLOY_PARENT_DIR="$(dirname "$DEPLOY_BASE_DIR")"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.8.8-eclipse-temurin-8}"
HOST_MAVEN_REPO="${HOST_MAVEN_REPO:-$DEPLOY_PARENT_DIR/.m2/repository}"
BACKEND_ARTIFACT_DIR="${BACKEND_ARTIFACT_DIR:-$DEPLOY_BASE_DIR/.deploy/mealserver}"
BACKEND_JAR_NAME="${BACKEND_JAR_NAME:-eladmin-system-1.1.jar}"
SKIP_REPO_UPDATE="${SKIP_REPO_UPDATE:-false}"
PREVIOUS_COMMIT="${PREVIOUS_COMMIT:-}"
BACKEND_DEPLOYED=false
BACKEND_STOPPED=false
PREVIOUS_BACKEND_TAG=""

restore_backend_on_failure() {
  local status=$?
  trap - EXIT
  if [[ "$status" -ne 0 && "$BACKEND_STOPPED" == "true" ]]; then
    log "backend build/deploy failed; restarting previous backend image mealserver:$PREVIOUS_BACKEND_TAG"
    if ! start_backend_image "$PREVIOUS_BACKEND_TAG" "${PREVIOUS_COMMIT:-unknown}"; then
      log "ERROR: previous backend image could not be restarted"
    fi
  fi
  exit "$status"
}
trap restore_backend_on_failure EXIT

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
  printf '[%s] %s\n' "$(timestamp)" "$*" >&2
}

get_free_disk_mb() {
  df -Pm "$DOCKER_DISK_PATH" 2>/dev/null | awk 'NR==2 {print $4}'
}

prune_build_cache() {
  log "cleaning Docker build cache"
  docker builder prune -af >/dev/null 2>&1 || log "warning: docker builder prune failed"
}

cleanup_docker_space() {
  local available_mb

  log "cleaning unused Docker containers and dangling images before rebuild"
  docker container prune -f >/dev/null 2>&1 || log "warning: docker container prune failed"
  docker image prune -f >/dev/null 2>&1 || log "warning: docker image prune failed"

  case "$DOCKER_BUILD_CACHE_PRUNE" in
    always)
      prune_build_cache
      ;;
    never)
      log "skipping Docker build cache cleanup (DOCKER_BUILD_CACHE_PRUNE=never)"
      ;;
    auto)
      available_mb=$(get_free_disk_mb || true)
      if [[ -z "$available_mb" ]]; then
        log "warning: unable to read free disk space for $DOCKER_DISK_PATH, preserving Docker build cache"
      elif (( available_mb < MIN_FREE_DISK_MB )); then
        log "free disk space is low (${available_mb}MB), cleaning Docker build cache"
        prune_build_cache
      else
        log "preserving Docker build cache (${available_mb}MB free) to speed up Maven and Node builds"
      fi
      ;;
    *)
      log "DOCKER_BUILD_CACHE_PRUNE must be auto, always, or never (got: $DOCKER_BUILD_CACHE_PRUNE)"
      exit 1
      ;;
  esac
}

check_disk_space() {
  local available_mb

  available_mb=$(get_free_disk_mb || true)
  if [[ -z "$available_mb" ]]; then
    log "warning: unable to read free disk space for $DOCKER_DISK_PATH"
    return 0
  fi

  if (( available_mb < MIN_FREE_DISK_MB )); then
    log "insufficient free disk space for Docker build: ${available_mb}MB available on $DOCKER_DISK_PATH, require at least ${MIN_FREE_DISK_MB}MB"
    log "hint: run 'docker system df' and clean /var/lib/docker or host logs before retrying"
    exit 1
  fi

  log "free disk space check passed: ${available_mb}MB available on $DOCKER_DISK_PATH"
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

# 只在后端源码变化时构建后端；前端变更由本地静态产物发布器处理。
determine_build_targets() {
  local changed_files="$1"

  local rebuild_backend=false
  local frontend_changed=false

  if [[ "$changed_files" == "force-full-rebuild" ]]; then
    rebuild_backend=true
    frontend_changed=true
  elif [[ -n "$changed_files" ]]; then
    if grep -qE '^eladmin/' <<< "$changed_files" || grep -qE '^docker/mealserver/' <<< "$changed_files"; then
      rebuild_backend=true
    fi
    if grep -qE '^(eladmin-web/|docker/mealweb/|docker/docker-compose\.yml$)' <<< "$changed_files"; then
      frontend_changed=true
    fi
  fi

  echo "backend=$rebuild_backend,frontend_changed=$frontend_changed"
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

build_backend_artifacts() {
  log "building backend artifacts with host Maven repository: $HOST_MAVEN_REPO"
  mkdir -p "$HOST_MAVEN_REPO" "$BACKEND_ARTIFACT_DIR/lib"
  rm -f "$BACKEND_ARTIFACT_DIR/app.jar"
  rm -rf "$BACKEND_ARTIFACT_DIR/lib"
  mkdir -p "$BACKEND_ARTIFACT_DIR/lib"

  docker run --rm \
    -v "$DEPLOY_BASE_DIR/eladmin:/workspace" \
    -v "$DEPLOY_BASE_DIR/docker/mealserver/settings.xml:/tmp/settings.xml:ro" \
    -v "$HOST_MAVEN_REPO:/root/.m2/repository" \
    -v "$BACKEND_ARTIFACT_DIR:/output" \
    -w /workspace \
    "$MAVEN_IMAGE" \
    sh -c "mvn -s /tmp/settings.xml -pl eladmin-system -am clean install -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true && \
      mvn -s /tmp/settings.xml -pl eladmin-system dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/output/lib && \
      cp eladmin-system/target/$BACKEND_JAR_NAME /output/app.jar"

  require_file "$BACKEND_ARTIFACT_DIR/app.jar"
}

get_container_image_tag() {
  local image
  image="$(docker inspect mealserver --format '{{.Config.Image}}' 2>/dev/null || true)"
  if [[ "$image" == mealserver:* ]]; then
    printf '%s\n' "${image#mealserver:}"
  fi
}

start_backend_image() {
  local image_tag="$1" git_commit="$2"
  export BACKEND_IMAGE_TAG="$image_tag"
  export IMAGE_TAG="$image_tag"
  export BACKEND_GIT_COMMIT="$git_commit"
  if ! (cd "$DEPLOY_BASE_DIR" && docker compose -f "$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL" --env-file "$ENV_FILE" up -d --no-build --no-deps backend); then
    return 1
  fi
  local attempt status
  for ((attempt = 1; attempt <= 180 / 5; attempt++)); do
    status="$(docker inspect mealserver --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' 2>/dev/null || true)"
    [[ "$status" == healthy ]] && return 0
    [[ "$status" == unhealthy ]] && return 1
    sleep 5
  done
  return 1
}

deploy_compose() {
  local previous_commit="$1"
  local compose_file="$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL"

  require_file "$compose_file"
  require_file "$ENV_FILE"

  # 检测并分类变更
  local changed_files
  local build_targets
  changed_files=$(detect_changes "$previous_commit")
  build_targets=$(determine_build_targets "$changed_files")

  local rebuild_backend frontend_changed
  rebuild_backend=$(echo "$build_targets" | grep -oP 'backend=\K(true|false)' || echo "false")
  frontend_changed=$(echo "$build_targets" | grep -oP 'frontend_changed=\K(true|false)' || echo "false")

  log "change analysis complete:"
  log "  - build backend: $rebuild_backend"
  log "  - frontend release required: $frontend_changed"
  if [[ "$frontend_changed" == "true" ]]; then
    log "前端源码或运行配置有变化；请从开发机执行 scripts/deploy-frontend-local.sh。服务器不会构建或重启 frontend。"
  fi
  if [[ "$rebuild_backend" != "true" ]]; then
    log "后端源码没有变化，不构建、停止或重启任何容器。"
    return 0
  fi

  local backend_tag backend_commit
  backend_tag=$(date '+%Y%m%d%H%M%S')
  backend_commit=$(git -C "$DEPLOY_BASE_DIR" rev-parse HEAD)
  export BACKEND_IMAGE_TAG="$backend_tag"
  export BACKEND_GIT_COMMIT="$backend_commit"

  log "building backend image mealserver:$BACKEND_IMAGE_TAG from $BACKEND_GIT_COMMIT"
  cleanup_docker_space
  check_disk_space
  retry_until_success 3 "pulling $MAVEN_IMAGE" docker pull "$MAVEN_IMAGE"
  retry_until_success 3 "pulling eclipse-temurin:8-jre" docker pull eclipse-temurin:8-jre
  PREVIOUS_BACKEND_TAG="$(get_container_image_tag)"
  if [[ -n "$PREVIOUS_BACKEND_TAG" ]]; then
    log "stopping backend only while Maven builds; frontend stays available"
    BACKEND_STOPPED=true
    (cd "$DEPLOY_BASE_DIR" && docker compose -f "$compose_file" --env-file "$ENV_FILE" stop backend)
  fi
  build_backend_artifacts
  (cd "$DEPLOY_BASE_DIR" && DOCKER_BUILDKIT=1 docker compose -f "$compose_file" --env-file "$ENV_FILE" build backend)
  if ! start_backend_image "$BACKEND_IMAGE_TAG" "$BACKEND_GIT_COMMIT"; then
    die "new backend image did not become healthy"
  fi
  BACKEND_STOPPED=false
  BACKEND_DEPLOYED=true
}

# 清理旧版本的 Docker 镜像
cleanup_old_images() {
  log "正在清理旧后端 Docker 镜像（保留最近 $KEEP_IMAGE_COUNT 个版本）..."
  log "前端旧 mealweb 镜像保留到首次切换和回退演练完成后，再按操作手册清理。"

  local old_mealserver_images cleaned_count=0
  old_mealserver_images=$(docker images --format '{{.Repository}}:{{.Tag}}|{{.ID}}|{{.CreatedAt}}' | \
    grep "^mealserver:" | \
    sort -t '|' -k3 -r | \
    tail -n +$((KEEP_IMAGE_COUNT + 1)) || true)

  if [[ -n "$old_mealserver_images" ]]; then
    while IFS='|' read -r image_name image_id image_created; do
      [[ -n "$image_name" ]] || continue
      log "  删除旧后端镜像: $image_name (创建时间: $image_created)"
      if remove_output=$(docker rmi "$image_name" 2>&1); then
        ((cleaned_count++)) || true
      else
        log "  警告: 无法删除镜像 $image_name: $remove_output"
      fi
    done <<< "$old_mealserver_images"
  fi

  log "清理完成: 共删除 $cleaned_count 个旧后端镜像"
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
  require_file "$ENV_FILE"

  local previous_commit
  if [[ "$SKIP_REPO_UPDATE" == "true" ]]; then
    previous_commit="$PREVIOUS_COMMIT"
    log "repository update skipped by bootstrap script"
  else
    previous_commit=$(prepare_repo)
  fi

  require_file "$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL"
  (cd "$DEPLOY_BASE_DIR" && docker compose -f "$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL" --env-file "$ENV_FILE" config --quiet)
  deploy_compose "$previous_commit"
  if [[ "$BACKEND_DEPLOYED" == "true" ]]; then
    cleanup_old_images
  fi
  print_summary
}

main "$@"
