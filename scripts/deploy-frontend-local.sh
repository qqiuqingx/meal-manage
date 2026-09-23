#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
WEB_DIR="$REPO_ROOT/eladmin-web"
DEPLOY_TARGET="${DEPLOY_TARGET:-122}"
DEPLOY_BASE_DIR="${DEPLOY_BASE_DIR:-/data/meals/meal-manage}"
ENV_FILE="${ENV_FILE:-/data/meals/.env}"
COMPOSE_FILE_REL="${COMPOSE_FILE_REL:-docker/docker-compose.yml}"
FRONTEND_RELEASE_ROOT="${FRONTEND_RELEASE_ROOT:-/data/meals/frontend}"
FRONTEND_RELEASE_KEEP="${FRONTEND_RELEASE_KEEP:-}"
SSH_CONNECT_TIMEOUT=15

TMP_DIR=""
REMOTE_ARCHIVE_TMP=""
REMOTE_CHECKSUM_TMP=""
REMOTE_ARCHIVE=""
REMOTE_CHECKSUM=""
REMOTE_MANAGER_TMP=""
PUBLISH_REMOTE_FILES=false
REMOTE_ARCHIVE=""
REMOTE_CHECKSUM=""
PUBLISH_RELEASE_ID=""
PUBLISH_COMMIT=""
LOCAL_ARCHIVE=""
NODE_INSTALL_LOCK_DIR=""
NODE_INSTALL_TMP_DIR=""

log() { printf '[frontend-deploy] %s\n' "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

cleanup() {
  local status=$?
  if [[ -n "$NODE_INSTALL_TMP_DIR" && -d "$NODE_INSTALL_TMP_DIR" ]]; then
    rm -rf -- "$NODE_INSTALL_TMP_DIR"
  fi
  if [[ -n "$NODE_INSTALL_LOCK_DIR" && -d "$NODE_INSTALL_LOCK_DIR" ]]; then
    rmdir "$NODE_INSTALL_LOCK_DIR" 2>/dev/null || true
  fi
  if [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]]; then
    rm -rf -- "$TMP_DIR"
  fi
  if [[ "$PUBLISH_REMOTE_FILES" == true ]]; then
    ssh -o "ConnectTimeout=$SSH_CONNECT_TIMEOUT" "$DEPLOY_TARGET" \
      "rm -f -- '$REMOTE_ARCHIVE_TMP' '$REMOTE_CHECKSUM_TMP' '$REMOTE_MANAGER_TMP' '$REMOTE_ARCHIVE' '$REMOTE_CHECKSUM'" \
      >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap cleanup EXIT

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "缺少必要命令：$1"
}

bootstrap_local_node() {
  local command_name expected current system architecture dist_platform dist_arch archive_name
  local runtime_root runtime_dir lock_dir temp_dir expected_sha actual_sha attempt
  expected="$(tr -d '[:space:]' < "$WEB_DIR/.nvmrc")"
  if command -v node >/dev/null 2>&1; then
    current="$(node --version)"
    current="${current#v}"
    [[ "$current" == "$expected" ]] && return 0
    log "当前 Node $current 与 .nvmrc 的 $expected 不同；本次发布将使用项目本地 Node，不改系统默认版本"
  else
    log "未检测到 Node；本次发布将下载 .nvmrc 指定的项目本地 Node"
  fi

  for command_name in curl shasum tar awk uname mkdir mktemp sleep mv chmod rmdir env; do
    require_cmd "$command_name"
  done
  system="$(uname -s)"
  architecture="$(uname -m)"
  case "$system:$architecture" in
    Darwin:arm64|Darwin:aarch64) dist_platform=darwin; dist_arch=arm64 ;;
    Darwin:x86_64) dist_platform=darwin; dist_arch=x64 ;;
    Linux:x86_64) dist_platform=linux; dist_arch=x64 ;;
    Linux:aarch64|Linux:arm64) dist_platform=linux; dist_arch=arm64 ;;
    *) die "暂不支持自动准备 Node 的平台：$system/$architecture" ;;
  esac

  runtime_root="$WEB_DIR/.node-runtime"
  runtime_dir="$runtime_root/node-v$expected"
  lock_dir="$runtime_root/.install-lock"
  mkdir -p "$runtime_root"
  chmod 0700 "$runtime_root"
  if [[ -x "$runtime_dir/bin/node" && -x "$runtime_dir/bin/npm" && "$("$runtime_dir/bin/node" --version 2>/dev/null)" == "v$expected" ]]; then
    exec env PATH="$runtime_dir/bin:$PATH" "$SCRIPT_DIR/deploy-frontend-local.sh" "$@"
  fi

  local owns_install_lock=false
  for ((attempt = 1; attempt <= 120; attempt++)); do
    if mkdir "$lock_dir" 2>/dev/null; then
      NODE_INSTALL_LOCK_DIR="$lock_dir"
      owns_install_lock=true
      break
    fi
    if [[ -x "$runtime_dir/bin/node" && -x "$runtime_dir/bin/npm" && "$("$runtime_dir/bin/node" --version 2>/dev/null)" == "v$expected" ]]; then
      exec env PATH="$runtime_dir/bin:$PATH" "$SCRIPT_DIR/deploy-frontend-local.sh" "$@"
    fi
    sleep 1
  done
  [[ "$owns_install_lock" == true ]] || die "等待项目本地 Node 安装锁超时：$lock_dir"

  if [[ -e "$runtime_dir" || -L "$runtime_dir" ]]; then
    rm -rf -- "$runtime_dir"
  fi

  NODE_INSTALL_TMP_DIR="$(mktemp -d "$runtime_root/.install.XXXXXX")"
  temp_dir="$NODE_INSTALL_TMP_DIR"
  archive_name="node-v$expected-$dist_platform-$dist_arch.tar.xz"
  curl -fsS --retry 3 "https://nodejs.org/dist/v$expected/$archive_name" -o "$temp_dir/$archive_name"
  curl -fsS --retry 3 "https://nodejs.org/dist/v$expected/SHASUMS256.txt" -o "$temp_dir/SHASUMS256.txt"
  expected_sha="$(awk -v name="$archive_name" '$2 == name { print $1; exit }' "$temp_dir/SHASUMS256.txt")"
  [[ "$expected_sha" =~ ^[a-fA-F0-9]{64}$ ]] || die "Node 官方校验清单中找不到 $archive_name"
  actual_sha="$(shasum -a 256 "$temp_dir/$archive_name" | awk '{print $1}')"
  [[ "$actual_sha" == "$expected_sha" ]] || die "Node $expected 的官方 SHA256 校验失败"
  tar -xJf "$temp_dir/$archive_name" -C "$temp_dir"
  [[ -x "$temp_dir/node-v$expected-$dist_platform-$dist_arch/bin/node" && -x "$temp_dir/node-v$expected-$dist_platform-$dist_arch/bin/npm" ]] || die "Node $expected 解压后缺少 node 或 npm"

  if [[ ! -e "$runtime_dir" ]]; then
    mv "$temp_dir/node-v$expected-$dist_platform-$dist_arch" "$runtime_dir"
  fi
  rm -rf -- "$temp_dir"
  NODE_INSTALL_TMP_DIR=""
  rmdir "$NODE_INSTALL_LOCK_DIR"
  NODE_INSTALL_LOCK_DIR=""
  [[ -x "$runtime_dir/bin/node" && -x "$runtime_dir/bin/npm" ]] || die "项目本地 Node 安装未完成"
  exec env PATH="$runtime_dir/bin:$PATH" "$SCRIPT_DIR/deploy-frontend-local.sh" "$@"
}

validate_remote_path() {
  local label="$1" value="$2"
  [[ "$value" =~ ^/[A-Za-z0-9._/-]+$ ]] || die "$label 必须是仅包含字母、数字、点、下划线、斜线和连字符的绝对路径"
  [[ "$value" != *"/../"* && "$value" != */.. && "$value" != */./* ]] || die "$label 不能包含 . 或 .. 路径段"
}

check_requirements() {
  local command_name
  for command_name in bash git node npm tar shasum rsync ssh find chmod awk dirname mktemp cp rm tr cat sed date grep curl; do
    require_cmd "$command_name"
  done
  [[ "$DEPLOY_TARGET" =~ ^[A-Za-z0-9._@:-]+$ && "$DEPLOY_TARGET" != -* ]] || die "DEPLOY_TARGET 格式无效"
  validate_remote_path DEPLOY_BASE_DIR "$DEPLOY_BASE_DIR"
  validate_remote_path ENV_FILE "$ENV_FILE"
  validate_remote_path FRONTEND_RELEASE_ROOT "$FRONTEND_RELEASE_ROOT"
  [[ "$COMPOSE_FILE_REL" =~ ^[A-Za-z0-9._/-]+$ && "$COMPOSE_FILE_REL" != /* && "$COMPOSE_FILE_REL" != *".."* ]] || die "COMPOSE_FILE_REL 格式无效"
  [[ -z "$FRONTEND_RELEASE_KEEP" || "$FRONTEND_RELEASE_KEEP" =~ ^[1-9][0-9]*$ ]] || die "FRONTEND_RELEASE_KEEP 必须是正整数"
  [[ -f "$WEB_DIR/.nvmrc" ]] || die "缺少 eladmin-web/.nvmrc"
  [[ -f "$WEB_DIR/package.json" && -f "$WEB_DIR/package-lock.json" ]] || die "package.json 或 package-lock.json 不存在"
  git -C "$REPO_ROOT" ls-files --error-unmatch eladmin-web/package-lock.json >/dev/null 2>&1 || die "package-lock.json 尚未纳入 Git"
}

check_node_version() {
  local expected actual
  expected="$(tr -d '[:space:]' < "$WEB_DIR/.nvmrc")"
  actual="$(node --version)"
  actual="${actual#v}"
  [[ "$actual" == "$expected" ]] || die "Node 版本必须与 .nvmrc 一致（期望 $expected，当前 $actual）"
}

check_worktree() {
  local changes
  changes="$(git -C "$REPO_ROOT" status --porcelain --untracked-files=all -- \
    eladmin-web \
    docker/docker-compose.yml docker/.env.example docker/mealweb \
    docker/mealserver/Dockerfile \
    scripts/deploy-frontend-local.sh scripts/manage-frontend-release.sh \
    scripts/deploy-from-github.sh scripts/deploy-bootstrap.sh scripts/rollback.sh README.md)"
  [[ -z "$changes" ]] || {
    log "以下发布相关路径有未提交改动："
    printf '%s\n' "$changes" >&2
    die "请先提交这些改动，再从对应 Git commit 构建发布"
  }
  git -C "$REPO_ROOT" rev-parse --verify HEAD >/dev/null 2>&1 || die "当前没有可用于发布的 Git commit"
}

install_dependencies_if_needed() {
  local node_version npm_version platform arch package_fingerprint cache_key marker
  node_version="$(node --version)"
  npm_version="$(npm --version)"
  platform="$(node -p 'process.platform')"
  arch="$(node -p 'process.arch')"
  package_fingerprint="$(cd "$WEB_DIR" && shasum -a 256 package.json package-lock.json | shasum -a 256 | awk '{print $1}')"
  cache_key="$(printf '%s\n%s\n%s\n%s\n%s\n' "$package_fingerprint" "$node_version" "$npm_version" "$platform" "$arch" | shasum -a 256 | awk '{print $1}')"
  marker="$WEB_DIR/node_modules/.frontend-deploy-dependencies"

  if [[ ! -d "$WEB_DIR/node_modules" || ! -f "$marker" || "$(cat "$marker" 2>/dev/null || true)" != "$cache_key" ]]; then
    log "安装锁定依赖（Node $node_version / npm $npm_version / $platform-$arch）"
    (cd "$WEB_DIR" && npm ci --legacy-peer-deps)
    printf '%s\n' "$cache_key" > "$marker"
  else
    log "复用与 package-lock、Node/npm 和平台匹配的 node_modules"
  fi
}

build_release() {
  local commit commit_short release_id built_at package_lock_sha node_version
  commit="$(git -C "$REPO_ROOT" rev-parse HEAD)"
  commit_short="$(git -C "$REPO_ROOT" rev-parse --short=7 HEAD)"
  release_id="$(date '+%Y%m%d%H%M%S')-$commit_short"
  built_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  package_lock_sha="$(shasum -a 256 "$WEB_DIR/package-lock.json" | awk '{print $1}')"
  node_version="$(node --version)"

  TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/frontend-release.XXXXXX")"
  local build_dist_dir="$TMP_DIR/dist"
  install_dependencies_if_needed
  log "构建 release $release_id（$commit）"
  (cd "$WEB_DIR" && VUE_APP_BASE_API=/ npm run build:prod -- --dest "$build_dist_dir")
  [[ -f "$build_dist_dir/index.html" ]] || die "构建成功但临时 dist/index.html 不存在"
  if find "$build_dist_dir" -type l -print | grep -q .; then
    die "dist 中存在符号链接，拒绝打包"
  fi

  local payload_dir="$TMP_DIR/payload"
  mkdir -m 0755 "$payload_dir"
  cp -R "$build_dist_dir/." "$payload_dir/"
  RELEASE_ID="$release_id" GIT_COMMIT="$commit" BUILT_AT="$built_at" \
    PACKAGE_LOCK_SHA="$package_lock_sha" NODE_VERSION="${node_version#v}" \
    node -e 'process.stdout.write(JSON.stringify({releaseId:process.env.RELEASE_ID,gitCommit:process.env.GIT_COMMIT,builtAt:process.env.BUILT_AT,packageLockSha256:process.env.PACKAGE_LOCK_SHA,nodeVersion:process.env.NODE_VERSION})+"\n")' \
    > "$payload_dir/release.json"
  find "$payload_dir" -type d -exec chmod 0755 {} +
  find "$payload_dir" -type f -exec chmod 0644 {} +

  local archive="$TMP_DIR/frontend-release-$release_id.tar.gz"
  local checksum="$archive.sha256"
  tar -czf "$archive" -C "$payload_dir" .
  shasum -a 256 "$archive" | awk '{print $1}' > "$checksum"
  [[ "$(shasum -a 256 "$archive" | awk '{print $1}')" == "$(cat "$checksum")" ]] || die "本地产物 SHA256 复核失败"

  PUBLISH_RELEASE_ID="$release_id"
  PUBLISH_COMMIT="$commit"
  LOCAL_ARCHIVE="$archive"
}

publish_release() {
  local release_id="$1" commit="$2" archive="$3"
  local archive_name="frontend-release-$release_id.tar.gz"
  local checksum_name="$archive_name.sha256"
  local incoming="$FRONTEND_RELEASE_ROOT/incoming"
  local remote_archive_tmp="$incoming/$archive_name.uploading.$$"
  local remote_checksum_tmp="$incoming/$checksum_name.uploading.$$"
  local remote_archive="$incoming/$archive_name"
  local remote_checksum="$incoming/$checksum_name"
  local remote_manager="$DEPLOY_BASE_DIR/scripts/manage-frontend-release.sh"
  local remote_manager_tmp="$DEPLOY_BASE_DIR/scripts/.manage-frontend-release.$release_id.$$.tmp"

  REMOTE_ARCHIVE_TMP="$remote_archive_tmp"
  REMOTE_CHECKSUM_TMP="$remote_checksum_tmp"
  REMOTE_MANAGER_TMP="$remote_manager_tmp"
  PUBLISH_REMOTE_FILES=true

  ssh -o "ConnectTimeout=$SSH_CONNECT_TIMEOUT" "$DEPLOY_TARGET" \
    "mkdir -p -- '$incoming' '$FRONTEND_RELEASE_ROOT/lock' '$DEPLOY_BASE_DIR/scripts'"
  rsync -e "ssh -o ConnectTimeout=$SSH_CONNECT_TIMEOUT" -- "$SCRIPT_DIR/manage-frontend-release.sh" "$DEPLOY_TARGET:$remote_manager_tmp"
  ssh -o "ConnectTimeout=$SSH_CONNECT_TIMEOUT" "$DEPLOY_TARGET" \
    "chmod 0750 -- '$remote_manager_tmp' && mv -f -- '$remote_manager_tmp' '$remote_manager'"
  REMOTE_MANAGER_TMP=""

  rsync -e "ssh -o ConnectTimeout=$SSH_CONNECT_TIMEOUT" -- "$archive" "$DEPLOY_TARGET:$remote_archive_tmp"
  rsync -e "ssh -o ConnectTimeout=$SSH_CONNECT_TIMEOUT" -- "$archive.sha256" "$DEPLOY_TARGET:$remote_checksum_tmp"
  ssh -o "ConnectTimeout=$SSH_CONNECT_TIMEOUT" "$DEPLOY_TARGET" \
    "set -eu; test ! -e '$remote_archive' && test ! -L '$remote_archive'; test ! -e '$remote_checksum' && test ! -L '$remote_checksum'; mv -n -- '$remote_archive_tmp' '$remote_archive'; if test -e '$remote_archive_tmp'; then exit 1; fi; mv -n -- '$remote_checksum_tmp' '$remote_checksum'; if test -e '$remote_checksum_tmp'; then rm -f -- '$remote_archive'; exit 1; fi"
  REMOTE_ARCHIVE="$remote_archive"
  REMOTE_CHECKSUM="$remote_checksum"

  local install_command
  local keep_option=""
  if [[ -n "$FRONTEND_RELEASE_KEEP" ]]; then
    keep_option="--keep '$FRONTEND_RELEASE_KEEP'"
  fi
  install_command="'$remote_manager' --root '$FRONTEND_RELEASE_ROOT' --base-dir '$DEPLOY_BASE_DIR' --env-file '$ENV_FILE' --compose-file '$COMPOSE_FILE_REL' $keep_option install '$remote_archive' '$remote_checksum' '$release_id'"
  ssh -o "ConnectTimeout=$SSH_CONNECT_TIMEOUT" "$DEPLOY_TARGET" "$install_command"
  REMOTE_ARCHIVE=""
  REMOTE_CHECKSUM=""

  log "发布成功"
  log "  release ID : $release_id"
  log "  Git commit : $commit"
  log "  health URL : http://127.0.0.1:18080/release.json"
  log "  回退命令   : $remote_manager --root $FRONTEND_RELEASE_ROOT --base-dir $DEPLOY_BASE_DIR --env-file $ENV_FILE rollback"
}

main() {
  bootstrap_local_node "$@"
  check_requirements
  check_node_version
  check_worktree
  build_release
  [[ "$PUBLISH_RELEASE_ID" =~ ^[0-9]{14}-[a-f0-9]{7}$ ]] || die "生成的 release ID 无效"
  [[ "$PUBLISH_COMMIT" =~ ^[a-f0-9]{40}$ ]] || die "生成的 Git commit 无效"
  [[ -f "$LOCAL_ARCHIVE" && -f "$LOCAL_ARCHIVE.sha256" ]] || die "本地 release 产物不完整"
  publish_release "$PUBLISH_RELEASE_ID" "$PUBLISH_COMMIT" "$LOCAL_ARCHIVE"
}

main "$@"
