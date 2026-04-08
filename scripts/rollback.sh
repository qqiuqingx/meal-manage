#!/usr/bin/env bash

set -euo pipefail

# Rollback script: revert to the previous Docker image tag and git commit.
# Works in tandem with deploy-from-github.sh:
#   - Images are named mealserver:<timestamp> and mealweb:<timestamp>
#   - The deploy script keeps KEEP_IMAGE_COUNT (default 3) images on disk
#   - This script picks the second-most-recent tag as the rollback target

DEPLOY_BASE_DIR="${DEPLOY_BASE_DIR:-/data/meals/meal-manage}"
ENV_FILE="${ENV_FILE:-/data/meals/.env}"
COMPOSE_FILE_REL="${COMPOSE_FILE_REL:-docker/docker-compose.yml}"

timestamp() { date '+%Y-%m-%d %H:%M:%S'; }
log()        { printf '[%s] %s\n' "$(timestamp)" "$*"; }
die()        { log "ERROR: $*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

require_file() {
  [[ -f "$1" ]] || die "required file not found: $1"
}

# ──────────────────────────────────────────────
# 1. 解析出当前正在运行的 IMAGE_TAG
# ──────────────────────────────────────────────
get_running_tag() {
  # 读取正在运行的 mealserver 容器使用的 image tag
  docker inspect mealserver \
    --format '{{.Config.Image}}' 2>/dev/null \
    | sed 's/mealserver://' || echo ""
}

# ──────────────────────────────────────────────
# 2. 列出本地所有 mealserver 镜像，按创建时间排序
#    返回格式：每行 "tag\tcreated_at"
# ──────────────────────────────────────────────
list_available_tags() {
  docker images \
    --format '{{.Tag}}\t{{.CreatedAt}}' \
    --filter "reference=mealserver" \
    | grep -v '<none>' \
    | sort -t $'\t' -k2 -r   # 最新的在最上面
}

# ──────────────────────────────────────────────
# 3. 确定回退目标 tag
# ──────────────────────────────────────────────
pick_rollback_tag() {
  local current_tag="$1"
  local tags_table
  tags_table=$(list_available_tags)

  if [[ -z "$tags_table" ]]; then
    die "no mealserver images found locally, cannot rollback"
  fi

  local image_count
  image_count=$(echo "$tags_table" | grep -c .)
  if (( image_count < 2 )); then
    die "only 1 image found ($(echo "$tags_table" | awk '{print $1}' | head -1)); need at least 2 to rollback. Check KEEP_IMAGE_COUNT in deploy-from-github.sh"
  fi

  log "available mealserver images (newest first):"
  local i=0
  while IFS=$'\t' read -r tag created; do
    i=$((i + 1))
    local marker=""
    [[ "$tag" == "$current_tag" ]] && marker=" ← current"
    log "  [$i] mealserver:$tag  (created: $created)$marker"
  done <<< "$tags_table"

  # 交互式选择（非 TTY 时自动选上一个版本）
  local target_tag=""
  if [[ -t 0 ]]; then
    printf '\nEnter the number to rollback to [default: previous version]: '
    read -r choice
    if [[ -z "$choice" ]]; then
      # 默认：取排在 current_tag 之后的第一个
      target_tag=$(awk -v cur="$current_tag" '
        BEGIN { found=0 }
        {
          split($0, a, "\t")
          if (found) { print a[1]; exit }
          if (a[1] == cur) found=1
        }
      ' <<< "$tags_table")
    else
      target_tag=$(awk -v n="$choice" 'NR==n { split($0, a, "\t"); print a[1] }' <<< "$tags_table")
    fi
  else
    # 无交互：自动选上一个版本
    target_tag=$(awk -v cur="$current_tag" '
      BEGIN { found=0 }
      {
        split($0, a, "\t")
        if (found) { print a[1]; exit }
        if (a[1] == cur) found=1
      }
    ' <<< "$tags_table")
  fi

  [[ -z "$target_tag" ]] && die "could not determine rollback target (already at oldest version?)"
  [[ "$target_tag" == "$current_tag" ]] && die "rollback target is the same as current version: $current_tag"

  echo "$target_tag"
}

# ──────────────────────────────────────────────
# 4. 检查 mealweb 是否也有对应 tag
# ──────────────────────────────────────────────
verify_frontend_tag() {
  local tag="$1"
  if ! docker image inspect "mealweb:$tag" >/dev/null 2>&1; then
    log "WARNING: mealweb:$tag not found locally"
    log "  The backend will roll back but the frontend image is missing."
    if [[ -t 0 ]]; then
      printf 'Continue with backend rollback only? [y/N]: '
      read -r confirm
      [[ "$confirm" =~ ^[Yy]$ ]] || die "rollback aborted by user"
    else
      die "mealweb:$tag not found, aborting"
    fi
    echo "backend-only"
  else
    echo "full"
  fi
}

# ─────────────���────────────────────────────────
# 5. 回退 git 仓库到 IMAGE_TAG 对应的 commit
#    逻辑：查找 git log 中 commit 时间 <= tag 时间戳 的最近一次
# ──────────────────────────────────────────────
rollback_git() {
  local target_tag="$1"

  if [[ ! -d "$DEPLOY_BASE_DIR/.git" ]]; then
    log "WARNING: $DEPLOY_BASE_DIR is not a git repo, skipping git rollback"
    return
  fi

  # tag 是 YYYYmmddHHMMSS，转换为 ISO 格式供 git 使用
  local tag_ts="${target_tag:0:4}-${target_tag:4:2}-${target_tag:6:2} ${target_tag:8:2}:${target_tag:10:2}:${target_tag:12:2}"

  log "finding git commit at or before $tag_ts ..."
  local target_commit
  target_commit=$(git -C "$DEPLOY_BASE_DIR" log \
    --format="%H %ci" \
    --before="$tag_ts" \
    -1 | awk '{print $1}')

  if [[ -z "$target_commit" ]]; then
    log "WARNING: no git commit found before $tag_ts, skipping git rollback"
    return
  fi

  log "rolling back git repo to commit $target_commit"
  git -C "$DEPLOY_BASE_DIR" checkout --detach "$target_commit"
  log "git HEAD is now at $target_commit"
}

# ──────────────────────────────────────────────
# 6. 重启容器（不重建镜像）
# ──────────────────────────────────────────────
restart_with_tag() {
  local target_tag="$1"
  local frontend_status="$2"
  local compose_file="$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL"

  require_file "$compose_file"
  require_file "$ENV_FILE"

  export IMAGE_TAG="$target_tag"
  log "restarting services with IMAGE_TAG=$IMAGE_TAG ..."

  (
    cd "$DEPLOY_BASE_DIR"
    docker compose -f "$compose_file" --env-file "$ENV_FILE" down || true

    if [[ "$frontend_status" == "backend-only" ]]; then
      docker compose -f "$compose_file" --env-file "$ENV_FILE" up -d --no-build backend
    else
      docker compose -f "$compose_file" --env-file "$ENV_FILE" up -d --no-build
    fi
  )
}

# ──────────────────────────────────────────────
# main
# ───────────────────────────────────��──────────
main() {
  require_cmd git
  require_cmd docker
  docker compose version >/dev/null 2>&1 || die "docker compose plugin is required"

  local current_tag
  current_tag=$(get_running_tag)

  if [[ -z "$current_tag" ]]; then
    log "mealserver container is not running; listing all local images instead..."
    current_tag="__not_running__"
  else
    log "current running tag: $current_tag"
  fi

  local target_tag
  target_tag=$(pick_rollback_tag "$current_tag")
  log "rollback target: $target_tag"

  local frontend_status
  frontend_status=$(verify_frontend_tag "$target_tag")

  rollback_git "$target_tag"
  restart_with_tag "$target_tag" "$frontend_status"

  log "──────────────────────────────────────────────"
  log "rollback complete"
  log "  rolled back to image tag : $target_tag"
  log "  backend image            : mealserver:$target_tag"
  [[ "$frontend_status" == "full" ]] && \
  log "  frontend image           : mealweb:$target_tag"
  log "──────────────────────────────────────────────"
}

main "$@"
