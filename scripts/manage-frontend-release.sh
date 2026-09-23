#!/usr/bin/env bash

set -Eeuo pipefail
umask 027

ROOT="${FRONTEND_RELEASE_ROOT:-/data/meals/frontend}"
DEPLOY_BASE_DIR="${DEPLOY_BASE_DIR:-/data/meals/meal-manage}"
ENV_FILE="${ENV_FILE:-/data/meals/.env}"
COMPOSE_FILE_REL="${COMPOSE_FILE_REL:-docker/docker-compose.yml}"
KEEP="${FRONTEND_RELEASE_KEEP:-}"
HEALTH_URL="http://127.0.0.1:18080/release.json"
HEALTH_TIMEOUT_SECONDS=90
STAGING_DIR=""
INCOMING_ARCHIVE=""
INCOMING_CHECKSUM=""
COMPOSE_CONFIG_TMP=""

timestamp() { date '+%Y-%m-%d %H:%M:%S'; }
log() { printf '[%s] %s\n' "$(timestamp)" "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

usage() {
  cat >&2 <<'EOF'
用法:
  manage-frontend-release.sh [选项] install <归档.tar.gz> <归档.tar.gz.sha256> <release-id>
  manage-frontend-release.sh [选项] rollback [release-id]
  manage-frontend-release.sh [选项] list

选项:
  --root PATH          前端 release 根目录
  --base-dir PATH      部署仓库目录
  --env-file PATH      Docker Compose 环境文件
  --compose-file PATH  相对部署仓库的 Compose 文件
  --keep COUNT         保留的最新 release 数量
EOF
  exit 2
}

cleanup() {
  if [[ -n "$STAGING_DIR" && -d "$STAGING_DIR" ]]; then
    rm -rf -- "$STAGING_DIR"
  fi
  if [[ -n "$INCOMING_ARCHIVE" ]]; then
    rm -f -- "$INCOMING_ARCHIVE"
  fi
  if [[ -n "$INCOMING_CHECKSUM" ]]; then
    rm -f -- "$INCOMING_CHECKSUM"
  fi
  if [[ -n "$COMPOSE_CONFIG_TMP" ]]; then
    rm -f -- "$COMPOSE_CONFIG_TMP"
  fi
}
trap cleanup EXIT

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "缺少必要命令：$1"
}

valid_release_id() {
  [[ "$1" =~ ^[0-9]{14}-[a-f0-9]{7,40}$ ]]
}

prepare_root() {
  [[ "$ROOT" == /* && "$ROOT" != "/" ]] || die "release 根目录必须是非根目录的绝对路径：$ROOT"
  [[ ! -L "$ROOT" ]] || die "release 根目录不能是符号链接：$ROOT"
  mkdir -p -- "$ROOT"
  ROOT="$(cd "$ROOT" && pwd -P)"

  [[ -d "$DEPLOY_BASE_DIR" ]] || die "部署仓库目录不存在：$DEPLOY_BASE_DIR"
  DEPLOY_BASE_DIR="$(cd "$DEPLOY_BASE_DIR" && pwd -P)"
  [[ "$ROOT" != "$DEPLOY_BASE_DIR" ]] || die "release 根目录不能是部署仓库目录"
  case "$ROOT/" in "$DEPLOY_BASE_DIR/"*) die "release 根目录不能位于部署仓库内" ;; esac
  case "$DEPLOY_BASE_DIR/" in "$ROOT/"*) die "release 根目录不能包含部署仓库" ;; esac

  [[ "$KEEP" =~ ^[1-9][0-9]*$ ]] || die "FRONTEND_RELEASE_KEEP 必须是正整数：$KEEP"
  [[ "$HEALTH_URL" =~ ^https?://[^/]+/release\.json$ ]] || die "健康检查地址必须以 /release.json 结尾：$HEALTH_URL"

  local part
  for part in releases incoming staging lock; do
    [[ ! -L "$ROOT/$part" ]] || die "release 子目录不能是符号链接：$ROOT/$part"
    mkdir -p -- "$ROOT/$part"
    [[ -d "$ROOT/$part" ]] || die "release 路径不是目录：$ROOT/$part"
  done
  [[ ! -L "$ROOT/lock/release.lock" ]] || die "锁文件不能是符号链接"
  chmod 0750 "$ROOT" "$ROOT/releases" "$ROOT/incoming" "$ROOT/staging" "$ROOT/lock"
}

load_keep_count() {
  if [[ -z "$KEEP" ]]; then
    local configured_keep=""
    if [[ -f "$ENV_FILE" ]]; then
      configured_keep="$(awk -F= '
        /^[[:space:]]*#/ || index($0, "=") == 0 { next }
        {
          key=$1
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", key)
          if (key == "FRONTEND_RELEASE_KEEP") {
            value=substr($0, index($0, "=") + 1)
            sub(/[[:space:]]+#.*/, "", value)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            if ((value ~ /^".*"$/) || (value ~ /^'.*'$/))
              value=substr(value, 2, length(value) - 2)
          }
        }
        END { print value }
      ' "$ENV_FILE")"
    fi
    KEEP="${configured_keep:-5}"
  fi
  [[ "$KEEP" =~ ^[1-9][0-9]*$ ]] || die "FRONTEND_RELEASE_KEEP 必须是正整数：$KEEP"
}

take_lock() {
  exec 9>>"$ROOT/lock/release.lock"
  flock -x 9
}

check_runtime() {
  local command_name
  for command_name in python3 tar sha256sum flock docker curl find chmod mv ln readlink rm sort awk tr; do
    require_cmd "$command_name"
  done
  docker compose version >/dev/null 2>&1 || die "需要 Docker Compose v2"
  [[ -f "$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL" ]] || die "Compose 文件不存在：$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL"
  [[ -f "$DEPLOY_BASE_DIR/docker/mealweb/nginx.conf" ]] || die "Nginx 配置文件不存在：$DEPLOY_BASE_DIR/docker/mealweb/nginx.conf"
  [[ -f "$ENV_FILE" ]] || die "Compose 环境文件不存在：$ENV_FILE"

  local config_file
  COMPOSE_CONFIG_TMP="$(mktemp "$ROOT/lock/compose-config.XXXXXX")"
  chmod 0600 "$COMPOSE_CONFIG_TMP"
  if ! (cd "$DEPLOY_BASE_DIR" && docker compose -f "$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL" --env-file "$ENV_FILE" config --format json >"$COMPOSE_CONFIG_TMP"); then
    rm -f -- "$COMPOSE_CONFIG_TMP"
    COMPOSE_CONFIG_TMP=""
    die "Docker Compose 配置校验失败"
  fi
  if ! python3 - "$COMPOSE_CONFIG_TMP" "$ROOT/current" "$DEPLOY_BASE_DIR/docker/mealweb/nginx.conf" <<'PY'
import json
import os
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    config = json.load(stream)
frontend = config.get("services", {}).get("frontend")
if not frontend or frontend.get("image") != "nginx:1.30.5-alpine3.24":
    raise SystemExit("frontend 必须使用固定的 nginx:1.30.5-alpine3.24 镜像")
mounts = frontend.get("volumes", [])
static_mount = any(
    mount.get("type") == "bind"
    and os.path.normpath(mount.get("source", "")) == os.path.normpath(sys.argv[2])
    and mount.get("target") == "/usr/share/nginx/html"
    and mount.get("read_only") is True
    for mount in mounts
)
nginx_config_mount = any(
    mount.get("type") == "bind"
    and mount.get("target") == "/etc/nginx/conf.d/default.conf"
    and mount.get("read_only") is True
    and os.path.realpath(mount.get("source", "")) == os.path.realpath(sys.argv[3])
    for mount in mounts
)
if not static_mount:
    raise SystemExit("frontend 必须只读挂载 release 根目录的 current 到 /usr/share/nginx/html")
if not nginx_config_mount:
    raise SystemExit("frontend 必须只读挂载仓库的 docker/mealweb/nginx.conf")
PY
  then
    rm -f -- "$COMPOSE_CONFIG_TMP"
    COMPOSE_CONFIG_TMP=""
    die "frontend Compose 配置与静态 release 契约不一致"
  fi
  rm -f -- "$COMPOSE_CONFIG_TMP"
  COMPOSE_CONFIG_TMP=""
}

read_pointer() {
  local name="$1"
  local path="$ROOT/$name"
  local target
  if [[ -L "$path" ]]; then
    target="$(readlink "$path")"
    [[ "$target" =~ ^releases/([0-9]{14}-[a-f0-9]{7,40})$ ]] || die "$name 符号链接目标无效：$target"
    [[ -d "$ROOT/$target" && ! -L "$ROOT/$target" ]] || die "$name 指向的 release 不存在：$target"
    printf '%s\n' "$target"
  elif [[ -e "$path" ]]; then
    die "$name 必须是 release 符号链接：$path"
  else
    printf '\n'
  fi
}

release_metadata_is_valid() {
  local metadata="$1"
  local expected_id="$2"
  python3 - "$metadata" "$expected_id" <<'PY'
import json
import re
import sys
from datetime import datetime

with open(sys.argv[1], encoding="utf-8") as stream:
    data = json.load(stream)
required = {"releaseId", "gitCommit", "builtAt", "packageLockSha256", "nodeVersion"}
if set(data) != required:
    raise SystemExit("release.json 字段不符合协议")
if data["releaseId"] != sys.argv[2]:
    raise SystemExit("release.json releaseId 不匹配")
if not re.fullmatch(r"[0-9a-f]{40}", data["gitCommit"]):
    raise SystemExit("release.json gitCommit 无效")
if not re.fullmatch(r"[0-9a-f]{64}", data["packageLockSha256"]):
    raise SystemExit("release.json packageLockSha256 无效")
if not isinstance(data["nodeVersion"], str) or not data["nodeVersion"]:
    raise SystemExit("release.json nodeVersion 无效")
try:
    datetime.fromisoformat(data["builtAt"].replace("Z", "+00:00"))
except (TypeError, ValueError):
    raise SystemExit("release.json builtAt 无效")
PY
}

validate_archive() {
  local archive="$1"
  local expected_id="$2"
  python3 - "$archive" "$expected_id" <<'PY'
import re
import sys
import tarfile

archive, release_id = sys.argv[1:]
if not re.fullmatch(r"[0-9]{14}-[a-f0-9]{7,40}", release_id):
    raise SystemExit("release ID 格式无效")
seen = set()
has_index = False
has_metadata = False
total_size = 0
try:
    with tarfile.open(archive, "r:gz") as tar:
        members = tar.getmembers()
        if not members or len(members) > 50000:
            raise SystemExit("归档成员数量无效")
        for member in members:
            name = member.name
            if name in (".", "./") and member.isdir():
                continue
            if not name or name.startswith("/") or "\\" in name or "\x00" in name:
                raise SystemExit("归档包含无效路径")
            normalized = name[2:] if name.startswith("./") else name
            if normalized.endswith("/"):
                normalized = normalized[:-1]
            parts = normalized.split("/")
            if not normalized or any(part in ("", ".", "..") for part in parts):
                raise SystemExit("归档包含路径穿越或非规范路径")
            if normalized in seen:
                raise SystemExit("归档包含重复路径")
            seen.add(normalized)
            if not (member.isdir() or member.isfile()):
                raise SystemExit("归档只允许目录和普通文件")
            total_size += member.size
            if total_size > 2 * 1024 * 1024 * 1024:
                raise SystemExit("归档解压体积超过 2 GiB")
            if normalized == "index.html" and member.isfile():
                has_index = True
            if normalized == "release.json" and member.isfile():
                has_metadata = True
        if not has_index or not has_metadata:
            raise SystemExit("归档根目录必须包含 index.html 和 release.json")
except (tarfile.TarError, OSError) as error:
    raise SystemExit(f"归档读取失败：{error}")
PY
}

set_pointer() {
  local name="$1"
  local target="$2"
  local link="$ROOT/$name"
  local next="$ROOT/$name.next.$$"
  rm -f -- "$next"
  if [[ -z "$target" ]]; then
    rm -f -- "$link"
  else
    ln -s "$target" "$next"
    mv -Tf -- "$next" "$link"
  fi
}

compose_up_frontend() {
  (cd "$DEPLOY_BASE_DIR" && docker compose -f "$DEPLOY_BASE_DIR/$COMPOSE_FILE_REL" --env-file "$ENV_FILE" up -d --force-recreate --no-deps frontend)
}

wait_for_release() {
  local expected_id="$1"
  local origin="${HEALTH_URL%/release.json}"
  local response index_file asset_path status attempt
  response="$(mktemp "$ROOT/lock/health-json.XXXXXX")"
  index_file="$(mktemp "$ROOT/lock/health-index.XXXXXX")"
  chmod 0600 "$response" "$index_file"
  for ((attempt = 1; attempt <= HEALTH_TIMEOUT_SECONDS / 2; attempt++)); do
    status="$(curl --silent --show-error --connect-timeout 2 --max-time 5 -o "$response" -w '%{http_code}' "$HEALTH_URL?probe=$$-$attempt" 2>/dev/null || true)"
    if [[ "$status" == "200" ]] && python3 - "$response" "$expected_id" <<'PY'
import json
import sys
try:
    with open(sys.argv[1], encoding="utf-8") as stream:
        data = json.load(stream)
    raise SystemExit(0 if data.get("releaseId") == sys.argv[2] else 1)
except (OSError, ValueError):
    raise SystemExit(1)
PY
    then
      status="$(curl --silent --show-error --connect-timeout 2 --max-time 5 -o "$index_file" -w '%{http_code}' "$origin/index.html?probe=$$-$attempt" 2>/dev/null || true)"
      if [[ "$status" == "200" ]]; then
        asset_path="$(python3 - "$index_file" <<'PY'
from html.parser import HTMLParser
from urllib.parse import urlsplit
import sys

class Assets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.paths = []
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        value = attrs.get("src") if tag == "script" else attrs.get("href") if tag == "link" else None
        if not value:
            return
        parsed = urlsplit(value)
        if parsed.scheme or parsed.netloc or ".." in parsed.path.split("/"):
            return
        if parsed.path.lower().endswith((".js", ".css")):
            path = parsed.path if parsed.path.startswith("/") else "/" + parsed.path
            self.paths.append(path)

parser = Assets()
with open(sys.argv[1], encoding="utf-8") as stream:
    parser.feed(stream.read())
print(parser.paths[0] if parser.paths else "")
PY
        )"
        if [[ -n "$asset_path" ]] && curl --fail --silent --show-error --connect-timeout 2 --max-time 5 -o /dev/null "$origin$asset_path" 2>/dev/null; then
          rm -f -- "$response" "$index_file"
          return 0
        fi
      fi
    fi
    sleep 2
  done
  rm -f -- "$response" "$index_file"
  return 1
}

restore_after_failed_activation() {
  local old_current="$1"
  local old_previous="$2"
  set_pointer current "$old_current"
  set_pointer previous "$old_previous"
  if [[ -z "$old_current" ]]; then
    log "首次激活失败且没有旧 current；需按操作手册恢复旧 mealweb 镜像与 Git commit"
    return 1
  fi
  local old_id="${old_current#releases/}"
  if compose_up_frontend && wait_for_release "$old_id"; then
    log "已恢复旧 release $old_id"
    return 0
  fi
  log "自动恢复旧 release $old_id 失败；保留所有版本并需要人工处理"
  return 1
}

activate_release() {
  local target_id="$1"
  local old_current old_previous
  old_current="$(read_pointer current)"
  old_previous="$(read_pointer previous)"

  if [[ -n "$old_current" ]]; then
    set_pointer previous "$old_current"
  fi
  set_pointer current "releases/$target_id"

  if compose_up_frontend && wait_for_release "$target_id"; then
    log "frontend release $target_id 已通过 HTTP 检查"
    return 0
  fi

  log "frontend release $target_id 未通过 HTTP 检查，开始恢复"
  restore_after_failed_activation "$old_current" "$old_previous" || true
  return 1
}

cleanup_old_releases() {
  local current previous current_id previous_id
  current="$(read_pointer current)"
  previous="$(read_pointer previous)"
  current_id="${current#releases/}"
  previous_id="${previous#releases/}"

  local ids id count=0
  ids="$(for path in "$ROOT"/releases/*; do
    [[ -d "$path" && ! -L "$path" ]] || continue
    id="${path##*/}"
    valid_release_id "$id" && printf '%s\n' "$id"
  done | LC_ALL=C sort -r)"

  while IFS= read -r id; do
    [[ -n "$id" ]] || continue
    count=$((count + 1))
    if ((count <= KEEP)) || [[ "$id" == "$current_id" || "$id" == "$previous_id" ]]; then
      continue
    fi
    [[ "$id" =~ ^[0-9]{14}-[a-f0-9]{7,40}$ ]] || continue
    log "清理过期前端 release $id"
    rm -rf -- "$ROOT/releases/$id"
  done <<< "$ids"
}

install_release() {
  (($# == 3)) || usage
  local archive="$1" checksum="$2" release_id="$3"
  valid_release_id "$release_id" || die "release ID 格式无效：$release_id"
  [[ "${archive%/*}" == "$ROOT/incoming" && "${archive##*/}" == "frontend-release-$release_id.tar.gz" ]] || die "归档必须位于 incoming 且名称与 release ID 相符"
  [[ "${checksum%/*}" == "$ROOT/incoming" && "${checksum##*/}" == "frontend-release-$release_id.tar.gz.sha256" ]] || die "校验文件必须位于 incoming 且名称与归档相符"
  [[ -f "$archive" && ! -L "$archive" ]] || die "归档不存在或不是普通文件：$archive"
  [[ -f "$checksum" && ! -L "$checksum" ]] || die "SHA256 文件不存在或不是普通文件：$checksum"
  INCOMING_ARCHIVE="$archive"
  INCOMING_CHECKSUM="$checksum"

  local expected_sha actual_sha
  expected_sha="$(tr -d '[:space:]' < "$checksum")"
  [[ "$expected_sha" =~ ^[a-fA-F0-9]{64}$ ]] || die "SHA256 文件格式无效"
  actual_sha="$(sha256sum "$archive" | awk '{print $1}')"
  expected_sha="$(printf '%s' "$expected_sha" | tr '[:upper:]' '[:lower:]')"
  [[ "$expected_sha" == "$actual_sha" ]] || die "归档 SHA256 校验失败"
  validate_archive "$archive" "$release_id" || die "归档结构校验失败"

  local release_dir="$ROOT/releases/$release_id"
  [[ ! -e "$release_dir" && ! -L "$release_dir" ]] || die "release ID 已存在，禁止覆盖：$release_id"
  STAGING_DIR="$ROOT/staging/$release_id.$$"
  [[ ! -e "$STAGING_DIR" && ! -L "$STAGING_DIR" ]] || die "staging 目录已存在：$STAGING_DIR"
  mkdir -m 0700 -- "$STAGING_DIR"
  tar -xzf "$archive" --no-same-owner -C "$STAGING_DIR"
  [[ -f "$STAGING_DIR/index.html" && ! -L "$STAGING_DIR/index.html" ]] || die "release 缺少普通文件 index.html"
  [[ -f "$STAGING_DIR/release.json" && ! -L "$STAGING_DIR/release.json" ]] || die "release 缺少普通文件 release.json"
  release_metadata_is_valid "$STAGING_DIR/release.json" "$release_id" || die "release.json 校验失败"
  find "$STAGING_DIR" -type d -exec chmod 0755 {} +
  find "$STAGING_DIR" -type f -exec chmod 0644 {} +
  mv -- "$STAGING_DIR" "$release_dir"
  STAGING_DIR=""

  activate_release "$release_id" || die "release $release_id 激活失败"
  cleanup_old_releases
  log "release $release_id 安装完成"
}

rollback_release() {
  (($# <= 1)) || usage
  local requested="${1:-}"
  local target old_current old_previous
  old_current="$(read_pointer current)"
  old_previous="$(read_pointer previous)"
  [[ -n "$old_current" ]] || die "当前没有 active release，无法静态版本回退"
  if [[ -n "$requested" ]]; then
    valid_release_id "$requested" || die "release ID 格式无效：$requested"
    [[ -d "$ROOT/releases/$requested" && ! -L "$ROOT/releases/$requested" ]] || die "指定 release 不存在：$requested"
    [[ -f "$ROOT/releases/$requested/index.html" && -f "$ROOT/releases/$requested/release.json" ]] || die "指定 release 文件不完整：$requested"
    release_metadata_is_valid "$ROOT/releases/$requested/release.json" "$requested" || die "指定 release 元数据无效：$requested"
    target="releases/$requested"
  else
    [[ -n "$old_previous" ]] || die "previous release 不存在，无法默认回退"
    target="$old_previous"
  fi
  [[ "$target" != "$old_current" ]] || die "回退目标已经是 current"

  set_pointer current "$target"
  set_pointer previous "$old_current"
  if compose_up_frontend && wait_for_release "${target#releases/}"; then
    cleanup_old_releases
    log "已回退到 ${target#releases/}"
    return 0
  fi
  log "回退目标未通过 HTTP 检查，恢复原 current 和 previous"
  restore_after_failed_activation "$old_current" "$old_previous" || true
  return 1
}

list_releases() {
  local current previous current_id previous_id path id fields
  current="$(read_pointer current)"
  previous="$(read_pointer previous)"
  current_id="${current#releases/}"
  previous_id="${previous#releases/}"
  local output
  output="$(for path in "$ROOT"/releases/*; do
    [[ -d "$path" && ! -L "$path" ]] || continue
    id="${path##*/}"
    valid_release_id "$id" || continue
    [[ -f "$path/release.json" ]] || continue
    fields="$(python3 - "$path/release.json" "$id" <<'PY'
import json
import sys
try:
    with open(sys.argv[1], encoding="utf-8") as stream:
        data = json.load(stream)
    if data.get("releaseId") != sys.argv[2]:
        raise SystemExit(1)
    print(data.get("gitCommit", "") + "\t" + data.get("builtAt", ""))
except (OSError, ValueError):
    raise SystemExit(1)
PY
    )" || continue
    printf '%s\t%s\t%s\t%s\n' "$id" "$([[ "$id" == "$current_id" ]] && printf yes || printf no)" "$([[ "$id" == "$previous_id" ]] && printf yes || printf no)" "$fields"
  done | LC_ALL=C sort -r -t $'\t' -k1,1)"
  printf 'RELEASE_ID\tCURRENT\tPREVIOUS\tGIT_COMMIT\tBUILT_AT\n'
  [[ -z "$output" ]] || printf '%s\n' "$output"
}

main() {
  while (($#)); do
    case "$1" in
      --root) (($# >= 2)) || usage; ROOT="$2"; shift 2 ;;
      --base-dir) (($# >= 2)) || usage; DEPLOY_BASE_DIR="$2"; shift 2 ;;
      --env-file) (($# >= 2)) || usage; ENV_FILE="$2"; shift 2 ;;
      --compose-file) (($# >= 2)) || usage; COMPOSE_FILE_REL="$2"; shift 2 ;;
      --keep) (($# >= 2)) || usage; KEEP="$2"; shift 2 ;;
      --) shift; break ;;
      -*) usage ;;
      *) break ;;
    esac
  done
  (($#)) || usage
  local command="$1"
  shift
  case "$command" in install|rollback|list) ;; *) usage ;; esac
  [[ -f "$ENV_FILE" ]] || die "Compose 环境文件不存在：$ENV_FILE"
  load_keep_count
  prepare_root
  check_runtime
  take_lock
  case "$command" in
    install) install_release "$@" ;;
    rollback) rollback_release "$@" ;;
    list) list_releases "$@" ;;
  esac
}

main "$@"
