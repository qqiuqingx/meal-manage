#!/bin/sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/eladmin/eladmin-system/.env.local"

# Keep database, Redis, JWT and RSA secrets outside tracked YAML files.
# The ignored .env.local file is the single fixed place for local runtime config.
if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE" >&2
  exit 1
fi

if [ "$(uname -s)" != "Darwin" ]; then
  echo "This script opens separate macOS Terminal windows and only supports Darwin." >&2
  exit 1
fi

RUNTIME_DIR="/private/tmp/eladmin-local-start.$$"
mkdir -p "$RUNTIME_DIR"
WEB_PORT_VALUE="${WEB_PORT:-8013}"

# Do not silently leave an older process serving requests after the new runner
# fails to bind its port.  The caller must stop the stale process first.
require_free_port() {
  port="$1"
  service_name="$2"
  listener="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [ -n "$listener" ]; then
    echo "$service_name cannot start because port $port is already in use:" >&2
    echo "$listener" >&2
    echo "Stop the existing process, then run this script again." >&2
    exit 1
  fi
}

require_free_port 8000 "eladmin-system"
require_free_port 18081 "agent-service"
require_free_port "$WEB_PORT_VALUE" "eladmin-web"

write_runner() {
  runner_path="$1"
  service_name="$2"
  service_body="$3"

  cat > "$runner_path" <<EOF
#!/bin/zsh
set -e

ROOT_DIR="$ROOT_DIR"
ENV_FILE="$ENV_FILE"

echo "[$service_name] loading local env: \$ENV_FILE"
set -a
source "\$ENV_FILE"
set +a

load_local_zshrc() {
  # oh-my-zsh may return non-zero while preparing local cache directories.
  # Do not let that abort the service startup before jenv/mvn399 are loaded.
  set +e
  source ~/.zshrc
  set -e
}

build_maven_repo_args() {
  repo_local="\$1"
  if [ -n "\$repo_local" ]; then
    mkdir -p "\$repo_local"
    echo "-Dmaven.repo.local=\$repo_local"
  fi
}

$service_body
EOF
  chmod +x "$runner_path"
}

open_terminal() {
  window_title="$1"
  runner_path="$2"

  osascript <<EOF
tell application "Terminal"
  activate
  do script "printf '\\\\e]0;$window_title\\\\a'; zsh '$runner_path'"
end tell
EOF
}

# eladmin-system reads DB_*, REDIS_*, JWT_BASE64_SECRET and RSA_PRIVATE_KEY from .env.local.
write_runner "$RUNTIME_DIR/eladmin-system.command" "eladmin-system" '
cd "$ROOT_DIR/eladmin/eladmin-system"
load_local_zshrc
jenv shell 17
mvn399
if [ ${#AGENT_ACCESS_CONTEXT_SECRET} -lt 32 ]; then
  echo "AGENT_ACCESS_CONTEXT_SECRET must contain at least 32 characters in $ENV_FILE" >&2
  exit 1
fi
# Default to ~/.m2; opt into an isolated repository only when you need one for debugging.
MAVEN_REPO_ARG="$(build_maven_repo_args "${ELADMIN_SYSTEM_MAVEN_REPO_LOCAL:-${MAVEN_REPO_LOCAL:-}}")"
# Build eladmin-system together with all upstream reactor modules and install
# them into the same Maven repository used by spring-boot:run.  Running Maven
# only inside eladmin-system would otherwise reuse stale common/logging/tools
# artifacts from ~/.m2 after those sibling modules change.
echo "[eladmin-system] rebuilding and installing reactor dependencies"
cd "$ROOT_DIR/eladmin"
mvn -q ${=MAVEN_REPO_ARG} -pl eladmin-system -am clean install -DskipTests
cd "$ROOT_DIR/eladmin/eladmin-system"
exec mvn -q ${=MAVEN_REPO_ARG} spring-boot:run \
  -Dspring-boot.run.arguments="--login.code.dev-bypass=${LOGIN_CODE_DEV_BYPASS:-}"
'

# agent-service reuses AGENT_INTERNAL_TOKEN and points to the local eladmin-system context API.
write_runner "$RUNTIME_DIR/agent-service.command" "agent-service" '
cd "$ROOT_DIR/agent-service"
load_local_zshrc
jenv shell 17
mvn399
export AGENT_AI_ENABLED="${AGENT_AI_ENABLED:-false}"
export AGENT_DIAGNOSIS_TOOL_MODE_ENABLED="${AGENT_DIAGNOSIS_TOOL_MODE_ENABLED:-false}"
export AGENT_CONTEXT_BASE_URL="${AGENT_CONTEXT_BASE_URL:-http://localhost:8000}"
# Default to ~/.m2; opt into an isolated repository only when you need one for debugging.
MAVEN_REPO_ARG="$(build_maven_repo_args "${AGENT_SERVICE_MAVEN_REPO_LOCAL:-${MAVEN_REPO_LOCAL:-}}")"
# Agent menu/date parsing lives in this service; always rebuild its classes
# from the current working tree before starting it.
echo "[agent-service] clean compiling current source"
mvn -q ${=MAVEN_REPO_ARG} clean compile -DskipTests
exec mvn -q ${=MAVEN_REPO_ARG} spring-boot:run
'

# eladmin-web keeps its webpack dev-server logs in a separate window.
write_runner "$RUNTIME_DIR/eladmin-web.command" "eladmin-web" '
cd "$ROOT_DIR/eladmin-web"
export NODE_OPTIONS="${NODE_OPTIONS:---openssl-legacy-provider}"
export BROWSER="${BROWSER:-none}"
exec ./node_modules/.bin/vue-cli-service serve --port "${WEB_PORT:-8013}" --open false
'

open_terminal "eladmin-system :8000" "$RUNTIME_DIR/eladmin-system.command"
open_terminal "agent-service :18081" "$RUNTIME_DIR/agent-service.command"
open_terminal "eladmin-web :$WEB_PORT_VALUE" "$RUNTIME_DIR/eladmin-web.command"

echo "Opened three Terminal windows:"
echo "  eladmin-system: http://localhost:8000"
echo "  agent-service:  http://localhost:18081"
echo "  eladmin-web:    http://localhost:$WEB_PORT_VALUE"
echo
echo "Stop a service with Ctrl-C in its own Terminal window."
