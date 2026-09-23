#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
MANAGER="$REPO_ROOT/scripts/manage-frontend-release.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/frontend-release-test.XXXXXX")"
TEST_ROOT="$(cd "$TEST_ROOT" && pwd -P)"
BIN="$TEST_ROOT/bin"
RELEASE_ROOT="$TEST_ROOT/releases"
BASE_DIR="$TEST_ROOT/deploy"
ENV_FILE="$TEST_ROOT/deploy.env"
REAL_TAR="$(command -v tar)"
REAL_SHASUM="$(command -v shasum)"

cleanup() { rm -rf -- "$TEST_ROOT"; }
trap cleanup EXIT

mkdir -p "$BIN" "$BASE_DIR/docker/mealweb" "$BASE_DIR/docker" "$RELEASE_ROOT/incoming"
: > "$BASE_DIR/docker/docker-compose.yml"
: > "$BASE_DIR/docker/mealweb/nginx.conf"
: > "$ENV_FILE"

cat > "$BIN/flock" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB

cat > "$BIN/sha256sum" <<'STUB'
#!/usr/bin/env bash
exec "$TEST_REAL_SHASUM" -a 256 "$@"
STUB

cat > "$BIN/tar" <<'STUB'
#!/usr/bin/env bash
if [[ "${1:-}" == "-xzf" ]]; then
  archive="$2"
  shift 2
  destination=""
  while (($#)); do
    case "$1" in
      --no-same-owner) shift ;;
      -C) destination="$2"; shift 2 ;;
      *) echo "unexpected tar argument: $1" >&2; exit 2 ;;
    esac
  done
  exec "$TEST_REAL_TAR" -xzf "$archive" -C "$destination"
fi
exec "$TEST_REAL_TAR" "$@"
STUB

cat > "$BIN/mv" <<'STUB'
#!/usr/bin/env bash
operands=()
while (($#)); do
  case "$1" in
    --) shift; while (($#)); do operands+=("$1"); shift; done ;;
    -*) shift ;;
    *) operands+=("$1"); shift ;;
  esac
done
[[ "${#operands[@]}" == 2 ]] || exit 2
python3 - "${operands[0]}" "${operands[1]}" <<'PY'
import os
import sys
os.replace(sys.argv[1], sys.argv[2])
PY
STUB

cat > "$BIN/sleep" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB

cat > "$BIN/docker" <<'STUB'
#!/usr/bin/env bash
if [[ "${1:-}" == compose && " $* " == *" version "* ]]; then
  echo 'Docker Compose version v2.30.0'
  exit 0
fi
if [[ "${1:-}" == compose && " $* " == *" config "* ]]; then
  python3 - "$TEST_FRONTEND_ROOT" "$TEST_BASE_DIR" <<'PY'
import json
import sys
root, base = sys.argv[1:]
print(json.dumps({"services":{"frontend":{"image":"nginx:1.30.5-alpine3.24","volumes":[
  {"type":"bind","source":root+"/current","target":"/usr/share/nginx/html","read_only":True},
  {"type":"bind","source":base+"/docker/mealweb/nginx.conf","target":"/etc/nginx/conf.d/default.conf","read_only":True}
]}}}))
PY
  exit 0
fi
if [[ "${1:-}" == compose && " $* " == *" up "* ]]; then
  exit 0
fi
echo "unexpected docker command: $*" >&2
exit 2
STUB

cat > "$BIN/curl" <<'STUB'
#!/usr/bin/env bash
output=""
format=""
url=""
fail_on_http=false
while (($#)); do
  case "$1" in
    -o) output="$2"; shift 2 ;;
    -w) format="$2"; shift 2 ;;
    --fail) fail_on_http=true; shift ;;
    -*) shift ;;
    *) url="$1"; shift ;;
  esac
done
path="${url%%\?*}"
status=200
if [[ "$path" == */release.json ]]; then
  source="$TEST_FRONTEND_ROOT/current/release.json"
  if [[ -n "${TEST_FAIL_RELEASE_ID:-}" && -f "$source" ]] && \
     [[ "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["releaseId"])' "$source")" == "$TEST_FAIL_RELEASE_ID" ]]; then
    status=503
  fi
elif [[ "$path" == */index.html ]]; then
  source="$TEST_FRONTEND_ROOT/current/index.html"
elif [[ "$path" == */static/* ]]; then
  source="$TEST_FRONTEND_ROOT/current/${path#*18080/}"
  [[ -f "$source" ]] || status=404
else
  status=404
fi
if [[ "$status" == 200 && -n "$output" && "$output" != /dev/null ]]; then
  cp "$source" "$output"
fi
if [[ -n "$format" ]]; then
  printf '%s' "$status"
fi
if [[ "$fail_on_http" == true && "$status" -ge 400 ]]; then
  exit 22
fi
exit 0
STUB

chmod +x "$BIN"/*
export PATH="$BIN:$PATH"
export TEST_REAL_TAR="$REAL_TAR"
export TEST_REAL_SHASUM="$REAL_SHASUM"
export TEST_FRONTEND_ROOT="$RELEASE_ROOT"
export TEST_BASE_DIR="$BASE_DIR"

manager() {
  FRONTEND_RELEASE_ROOT="$RELEASE_ROOT" \
  DEPLOY_BASE_DIR="$BASE_DIR" \
  ENV_FILE="$ENV_FILE" \
  FRONTEND_RELEASE_KEEP=2 \
    bash "$MANAGER" --root "$RELEASE_ROOT" --base-dir "$BASE_DIR" --env-file "$ENV_FILE" "$@"
}

make_release() {
  local release_id="$1" mode="${2:-valid}"
  local payload="$TEST_ROOT/payload-$release_id"
  local archive="$RELEASE_ROOT/incoming/frontend-release-$release_id.tar.gz"
  local checksum="$archive.sha256"
  mkdir -p "$payload/static"
  printf '<!doctype html><script src="/static/app.js"></script>\n' > "$payload/index.html"
  printf 'window.release = true;\n' > "$payload/static/app.js"
  RELEASE_ID="$release_id" MODE="$mode" python3 - "$payload" "$archive" <<'PY'
import io
import json
import os
import sys
import tarfile

payload, archive = sys.argv[1:]
release_id = os.environ["RELEASE_ID"]
mode = os.environ["MODE"]
metadata_id = "20260923010099-abcdef9" if mode == "wrong-id" else release_id
metadata = {
    "releaseId": metadata_id,
    "gitCommit": "a" * 40,
    "builtAt": "2026-09-23T01:00:00Z",
    "packageLockSha256": "b" * 64,
    "nodeVersion": "24.21.0",
}
with open(os.path.join(payload, "release.json"), "w", encoding="utf-8") as stream:
    json.dump(metadata, stream, separators=(",", ":"))
with tarfile.open(archive, "w:gz") as tar:
    root = tarfile.TarInfo("./")
    root.type = tarfile.DIRTYPE
    tar.addfile(root)
    if mode != "missing-index":
        tar.add(os.path.join(payload, "index.html"), arcname="./index.html")
    tar.add(os.path.join(payload, "release.json"), arcname="./release.json")
    tar.add(os.path.join(payload, "static"), arcname="./static")
    if mode == "traversal":
        info = tarfile.TarInfo("../escape")
        info.size = 1
        tar.addfile(info, io.BytesIO(b"x"))
    if mode == "symlink":
        info = tarfile.TarInfo("./static/escape")
        info.type = tarfile.SYMTYPE
        info.linkname = "../../outside"
        tar.addfile(info)
PY
  "$REAL_SHASUM" -a 256 "$archive" | awk '{print $1}' > "$checksum"
}

assert_pointer() {
  local name="$1" expected="$2" actual
  if [[ "$expected" == absent ]]; then
    [[ ! -e "$RELEASE_ROOT/$name" && ! -L "$RELEASE_ROOT/$name" ]] || {
      echo "expected $name to be absent" >&2; exit 1;
    }
    return
  fi
  actual="$(readlink "$RELEASE_ROOT/$name")"
  [[ "$actual" == "releases/$expected" ]] || {
    echo "expected $name -> releases/$expected, got $actual" >&2; exit 1;
  }
}

assert_install_fails_without_switch() {
  local release_id="$1" mode="$2" old_current="$3" old_previous="$4"
  make_release "$release_id" "$mode"
  if [[ "$mode" == bad-sha ]]; then
    printf '%064d\n' 0 > "$RELEASE_ROOT/incoming/frontend-release-$release_id.tar.gz.sha256"
  fi
  if manager install "$RELEASE_ROOT/incoming/frontend-release-$release_id.tar.gz" \
      "$RELEASE_ROOT/incoming/frontend-release-$release_id.tar.gz.sha256" "$release_id"; then
    echo "expected invalid $mode release to fail" >&2
    exit 1
  fi
  assert_pointer current "$old_current"
  assert_pointer previous "$old_previous"
}

ID1=20260923010001-abcdef1
ID2=20260923010002-abcdef2
ID3=20260923010003-abcdef3
ID4=20260923010004-abcdef4
ID5=20260923010005-abcdef5
ID6=20260923010006-abcdef6
ID7=20260923010007-abcdef7
ID8=20260923010008-abcdef8

make_release "$ID1"
manager install "$RELEASE_ROOT/incoming/frontend-release-$ID1.tar.gz" \
  "$RELEASE_ROOT/incoming/frontend-release-$ID1.tar.gz.sha256" "$ID1"
assert_pointer current "$ID1"
assert_pointer previous absent

python3 - "$RELEASE_ROOT/current" <<'PY'
import os
import stat
import sys
root = sys.argv[1]
assert stat.S_IMODE(os.stat(os.path.join(root, "index.html")).st_mode) == 0o644
assert stat.S_IMODE(os.stat(root).st_mode) == 0o755
PY

make_release "$ID2"
manager install "$RELEASE_ROOT/incoming/frontend-release-$ID2.tar.gz" \
  "$RELEASE_ROOT/incoming/frontend-release-$ID2.tar.gz.sha256" "$ID2"
assert_pointer current "$ID2"
assert_pointer previous "$ID1"

assert_install_fails_without_switch "$ID3" bad-sha "$ID2" "$ID1"
assert_install_fails_without_switch "$ID4" traversal "$ID2" "$ID1"
assert_install_fails_without_switch "$ID5" symlink "$ID2" "$ID1"
assert_install_fails_without_switch "$ID6" missing-index "$ID2" "$ID1"
assert_install_fails_without_switch "$ID7" wrong-id "$ID2" "$ID1"

make_release "$ID8"
export TEST_FAIL_RELEASE_ID="$ID8"
if manager install "$RELEASE_ROOT/incoming/frontend-release-$ID8.tar.gz" \
    "$RELEASE_ROOT/incoming/frontend-release-$ID8.tar.gz.sha256" "$ID8"; then
  echo "expected failing health check to abort install" >&2
  exit 1
fi
unset TEST_FAIL_RELEASE_ID
assert_pointer current "$ID2"
assert_pointer previous "$ID1"

manager rollback
assert_pointer current "$ID1"
assert_pointer previous "$ID2"
manager rollback "$ID2"
assert_pointer current "$ID2"
assert_pointer previous "$ID1"

list_output="$(manager list)"
[[ "$list_output" == *"$ID2"* && "$list_output" == *"CURRENT"* ]] || {
  echo "release list is missing current metadata" >&2; exit 1;
}

echo "frontend release manager smoke tests passed"
