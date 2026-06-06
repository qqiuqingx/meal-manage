#!/usr/bin/env bash

set -euo pipefail

# Stable production entrypoint:
# 1. Update the deployment repository to the latest remote branch
# 2. Exec the latest deploy-from-github.sh from the updated repository
#
# Put this script on the server and run it for every deployment:
#   bash /data/meals/deploy-bootstrap.sh

REPO_URL="${REPO_URL:-git@github.com:qqiuqingx/meal-manage.git}"
BRANCH="${BRANCH:-master}"
DEPLOY_BASE_DIR="${DEPLOY_BASE_DIR:-/data/meals/meal-manage}"
DEPLOY_SCRIPT_REL="${DEPLOY_SCRIPT_REL:-scripts/deploy-from-github.sh}"

timestamp() {
  date '+%Y-%m-%d %H:%M:%S'
}

log() {
  printf '[%s] %s\n' "$(timestamp)" "$*" >&2
}

die() {
  log "ERROR: $*" >&2
  exit 1
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    die "missing required command: $1"
  fi
}

confirm_replace_deploy_dir() {
  if [[ "${FORCE_REPLACE_DEPLOY_DIR:-false}" == "true" ]]; then
    log "FORCE_REPLACE_DEPLOY_DIR=true, replacing non-git directory: $DEPLOY_BASE_DIR"
    return
  fi

  if [[ ! -t 0 ]]; then
    die "$DEPLOY_BASE_DIR exists but is not a git repository; refusing to delete without interactive confirmation"
  fi

  log "WARNING: $DEPLOY_BASE_DIR exists but is not a git repository."
  log "This directory must be removed before cloning $REPO_URL."
  printf 'Type DELETE to remove %s and continue: ' "$DEPLOY_BASE_DIR"

  local confirm
  read -r confirm
  if [[ "$confirm" != "DELETE" ]]; then
    die "deployment aborted; directory was not removed"
  fi
}

prepare_latest_repo() {
  local previous_commit=""

  mkdir -p "$(dirname "$DEPLOY_BASE_DIR")"

  if [[ -d "$DEPLOY_BASE_DIR/.git" ]]; then
    log "updating repository in $DEPLOY_BASE_DIR"
    previous_commit=$(git -C "$DEPLOY_BASE_DIR" rev-parse HEAD 2>/dev/null || true)

    if git -C "$DEPLOY_BASE_DIR" rev-parse --is-shallow-repository 2>/dev/null | grep -q true; then
      log "repository is shallow, fetching full history for change detection"
      git -C "$DEPLOY_BASE_DIR" fetch --unshallow
    fi

    git -C "$DEPLOY_BASE_DIR" fetch origin "$BRANCH"
    git -C "$DEPLOY_BASE_DIR" checkout "$BRANCH"
    git -C "$DEPLOY_BASE_DIR" reset --hard "origin/$BRANCH"
  else
    if [[ -e "$DEPLOY_BASE_DIR" ]]; then
      confirm_replace_deploy_dir
      rm -rf "$DEPLOY_BASE_DIR"
    fi

    log "cloning repository $REPO_URL to $DEPLOY_BASE_DIR"
    git clone --branch "$BRANCH" "$REPO_URL" "$DEPLOY_BASE_DIR"
  fi

  printf '%s\n' "$previous_commit"
}

main() {
  require_cmd git
  require_cmd bash

  local previous_commit
  previous_commit=$(prepare_latest_repo)

  local deploy_script="$DEPLOY_BASE_DIR/$DEPLOY_SCRIPT_REL"
  if [[ ! -f "$deploy_script" ]]; then
    die "deploy script not found: $deploy_script"
  fi

  export SKIP_REPO_UPDATE=true
  export PREVIOUS_COMMIT="$previous_commit"

  log "executing latest deploy script: $deploy_script"
  exec bash "$deploy_script"
}

main "$@"
