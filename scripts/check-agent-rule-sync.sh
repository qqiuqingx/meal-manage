#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <previous_commit> <current_commit>" >&2
  exit 2
fi

previous_commit="$1"
current_commit="$2"
repo_dir="${REPO_DIR:-$(pwd)}"

if [[ -z "$previous_commit" || "$previous_commit" == "force-full-rebuild" ]]; then
  exit 0
fi

if ! git -C "$repo_dir" cat-file -e "$previous_commit" 2>/dev/null; then
  exit 0
fi

if ! git -C "$repo_dir" cat-file -e "$current_commit" 2>/dev/null; then
  echo "current commit not found: $current_commit" >&2
  exit 2
fi

changed_files=$(git -C "$repo_dir" diff --name-only "$previous_commit" "$current_commit")

if [[ -z "$changed_files" ]]; then
  exit 0
fi

diagnosis_code_pattern='^(agent-service/src/main/java/me/zhengjie/agent/(orchestrator|context|client|prompt|rule|validator)/|eladmin/eladmin-system/src/main/java/me/zhengjie/modules/agent/)'
rules_pattern='^agent-service/rules/'

if ! echo "$changed_files" | grep -qE "$diagnosis_code_pattern"; then
  exit 0
fi

if echo "$changed_files" | grep -qE "$rules_pattern"; then
  exit 0
fi

commit_messages=$(git -C "$repo_dir" log --format=%B "$previous_commit..$current_commit")

if echo "$commit_messages" | grep -q "rules-no-change"; then
  exit 0
fi

cat >&2 <<'MSG'
agent diagnosis code changed but agent-service/rules was not updated.
If rules are unchanged by design, include "rules-no-change" in commit message.
MSG
exit 1
