#!/usr/bin/env bash

set -euo pipefail

DEPLOY_BASE_DIR="${DEPLOY_BASE_DIR:-/data/meals/meal-manage}"
ENV_FILE="${ENV_FILE:-/data/meals/.env}"
COMPOSE_FILE_REL="${COMPOSE_FILE_REL:-docker/docker-compose.yml}"
FRONTEND_RELEASE_ROOT="${FRONTEND_RELEASE_ROOT:-/data/meals/frontend}"
HEALTH_TIMEOUT_SECONDS=180

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
die() { log "ERROR: $*" >&2; exit 1; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"; }

usage() {
  printf 'Usage: %s [mealserver-image-tag]\n' "$0" >&2
  exit 2
}

get_running_tag() {
  local image
  image="$(docker inspect mealserver --format '{{.Config.Image}}' 2>/dev/null || true)"
  [[ "$image" == mealserver:* ]] || return 1
  printf '%s\n' "${image#mealserver:}"
}

list_available_tags() {
  docker images --format '{{.Tag}}\t{{.CreatedAt}}' --filter 'reference=mealserver' | \
    awk -F '\t' '$1 != "<none>" { print }' | \
    LC_ALL=C sort -t $'\t' -k2,2r
}

choose_previous_tag() {
  local current_tag="$1"
  local found=false tag created
  while IFS=$'\t' read -r tag created; do
    [[ -n "$tag" ]] || continue
    if [[ "$found" == true ]]; then
      printf '%s\n' "$tag"
      return 0
    fi
    [[ "$tag" == "$current_tag" ]] && found=true
  done <<< "$(list_available_tags)"
  return 1
}

resolve_image_commit() {
  local tag="$1"
  local commit mapping
  commit="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "mealserver:$tag" 2>/dev/null || true)"
  if [[ "$commit" =~ ^[a-f0-9]{40}$ ]]; then
    printf '%s\n' "$commit"
    return 0
  fi

  mapping="$DEPLOY_BASE_DIR/.deploy/mealserver/revisions/$tag.commit"
  if [[ -f "$mapping" ]]; then
    commit="$(cat "$mapping")"
    if [[ "$commit" =~ ^[a-f0-9]{40}$ ]]; then
      printf '%s\n' "$commit"
      return 0
    fi
  fi
  return 1
}

wait_for_backend() {
  local attempt status
  for ((attempt = 1; attempt <= HEALTH_TIMEOUT_SECONDS / 5; attempt++)); do
    status="$(docker inspect mealserver --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' 2>/dev/null || true)"
    [[ "$status" == healthy ]] && return 0
    [[ "$status" == unhealthy ]] && return 1
    sleep 5
  done
  return 1
}

compose_backend() {
  local image_tag="$1" git_commit="$2"
  export BACKEND_IMAGE_TAG="$image_tag"
  export IMAGE_TAG="$image_tag" # 允许 Git 回退到切换前仍使用 IMAGE_TAG 的 Compose 提交。
  export BACKEND_GIT_COMMIT="$git_commit"
  (cd "$DEPLOY_BASE_DIR" && docker compose -f "$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL" --env-file "$ENV_FILE" up -d --no-build --no-deps backend)
}

show_frontend_release() {
  local current="$FRONTEND_RELEASE_ROOT/current"
  if [[ -L "$current" ]]; then
    local target
    target="$(readlink "$current")"
    printf '%s\n' "${target#releases/}"
  else
    printf '%s\n' unknown
  fi
}

main() {
  (($# <= 1)) || usage
  local requested_tag="${1:-}"
  local command_name current_tag target_tag target_commit current_commit current_branch_status

  for command_name in git docker readlink cat awk sort sleep; do
    require_cmd "$command_name"
  done
  docker compose version >/dev/null 2>&1 || die "docker compose plugin is required"
  [[ -d "$DEPLOY_BASE_DIR/.git" ]] || die "deployment directory is not a Git repository: $DEPLOY_BASE_DIR"
  [[ -f "$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL" && -f "$ENV_FILE" ]] || die "compose file or env file is missing"

  current_tag="$(get_running_tag)" || die "mealserver container is not running; cannot select a rollback target"
  if [[ -n "$requested_tag" ]]; then
    target_tag="$requested_tag"
    docker image inspect "mealserver:$target_tag" >/dev/null 2>&1 || die "mealserver image not found: $target_tag"
  else
    target_tag="$(choose_previous_tag "$current_tag")" || die "no previous mealserver image tag is available"
  fi
  [[ "$target_tag" != "$current_tag" ]] || die "rollback target is already running: $target_tag"
  [[ "$target_tag" =~ ^[A-Za-z0-9_.-]+$ ]] || die "invalid image tag: $target_tag"
  target_commit="$(resolve_image_commit "$target_tag")" || die "no verified Git commit is recorded for mealserver:$target_tag; record its confirmed commit in .deploy/mealserver/revisions/$target_tag.commit"

  git -C "$DEPLOY_BASE_DIR" cat-file -e "$target_commit^{commit}" 2>/dev/null || die "rollback commit is not present in the local repository: $target_commit"
  current_commit="$(git -C "$DEPLOY_BASE_DIR" rev-parse HEAD)"
  current_branch_status="$(git -C "$DEPLOY_BASE_DIR" status --porcelain --untracked-files=no)"
  [[ -z "$current_branch_status" ]] || die "deployment repository has tracked changes; refusing to overwrite them"

  log "current backend image: mealserver:$current_tag"
  log "deployment repository HEAD: $current_commit"
  log "target backend : mealserver:$target_tag ($target_commit)"
  log "frontend stays at release $(show_frontend_release)"

  git -C "$DEPLOY_BASE_DIR" checkout --detach "$target_commit"
  if ! (cd "$DEPLOY_BASE_DIR" && docker compose -f "$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL" --env-file "$ENV_FILE" config --quiet) || \
     ! compose_backend "$target_tag" "$target_commit" || ! wait_for_backend; then
    log "target backend failed to start or become healthy; restoring current backend"
    git -C "$DEPLOY_BASE_DIR" checkout --detach "$current_commit" || die "could not restore deployment Git commit $current_commit"
    if ! compose_backend "$current_tag" "$current_commit" || ! wait_for_backend; then
      die "backend rollback and automatic recovery both failed; Git is restored to $current_commit"
    fi
    die "rollback target failed health check; current backend was restored"
  fi

  log "rollback completed"
  log "  backend image : mealserver:$target_tag"
  log "  Git commit    : $target_commit"
  log "  frontend      : $(show_frontend_release) (unchanged)"
}

main "$@"
