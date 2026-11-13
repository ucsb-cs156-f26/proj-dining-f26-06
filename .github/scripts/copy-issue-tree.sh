#!/usr/bin/env bash
# Copy issues (and, recursively, their open sub-issues) from the starter repo
# into this repo, re-creating the parent/sub-issue links between the copies.
#
# Usage: copy-issue-tree.sh <starter-issue-number>...
#
# Environment:
#   STARTER_REPO    owner/name of the repo to copy from (required)
#   DEST_REPO       owner/name of the repo to copy into (default: $GITHUB_REPOSITORY)
#   COMMENT_SOURCE  "true" to add a "Copied from ..." comment to each new issue
#   DRY_RUN         "true" to read from the starter repo but create/link nothing
#   GH_TOKEN        token with issues:write on DEST_REPO (provided by Actions)
#
# An issue reachable more than once (e.g. listed at top level and also a
# sub-issue of another listed issue) is copied only once per run.

set -euo pipefail

: "${STARTER_REPO:?STARTER_REPO is required}"
DEST_REPO="${DEST_REPO:-${GITHUB_REPOSITORY:-}}"
: "${DEST_REPO:?DEST_REPO or GITHUB_REPOSITORY is required}"
COMMENT_SOURCE="${COMMENT_SOURCE:-false}"
DRY_RUN="${DRY_RUN:-false}"

MAP_FILE="$(mktemp)"   # lines of "<starter number> <new number>"
trap 'rm -f "$MAP_FILE"' EXIT

log() { echo "$*" >&2; }

summary() {
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    echo "$*" >> "$GITHUB_STEP_SUMMARY"
  fi
}

lookup() {
  awk -v n="$1" '$1 == n { print $2 }' "$MAP_FILE" | tail -n 1
}

# Prints the number of the copy of starter issue $1, creating it if needed.
copy_issue() {
  local n="$1" new json
  new="$(lookup "$n")"
  if [ -n "$new" ]; then
    echo "$new"
    return
  fi

  json="$(gh api "repos/$STARTER_REPO/issues/$n")"
  if [ "$DRY_RUN" = "true" ]; then
    new="DRY-$n"
  else
    new="$(jq '{title: .title, body: (.body // ""), labels: [.labels[].name]}' <<<"$json" \
      | gh api "repos/$DEST_REPO/issues" --input - --jq '.number')"
    if [ "$COMMENT_SOURCE" = "true" ]; then
      gh api "repos/$DEST_REPO/issues/$new/comments" \
        -f body="Copied from https://github.com/$STARTER_REPO/issues/$n" > /dev/null
    fi
  fi
  echo "$n $new" >> "$MAP_FILE"
  log "Copied $STARTER_REPO#$n -> $DEST_REPO#$new"
  summary "- $STARTER_REPO#$n copied to $DEST_REPO#$new"
  echo "$new"
}

# Copies the open sub-issues of starter issue $1 and links them under $2.
copy_sub_issues() {
  local n="$1" parent_new="$2" child child_new child_id
  local children
  children="$(gh api --paginate "repos/$STARTER_REPO/issues/$n/sub_issues" \
    | jq -r --arg repo "$STARTER_REPO" \
        '.[] | select(.state == "open") | select(.repository_url | endswith("/repos/" + $repo)) | .number')"

  for child in $children; do
    child_new="$(copy_issue "$child")"
    if [ "$DRY_RUN" = "true" ]; then
      log "[dry run] would link $DEST_REPO#$child_new under $DEST_REPO#$parent_new"
    else
      child_id="$(gh api "repos/$DEST_REPO/issues/$child_new" --jq '.id')"
      if ! gh api -X POST "repos/$DEST_REPO/issues/$parent_new/sub_issues" \
            -F sub_issue_id="$child_id" > /dev/null; then
        log "WARNING: could not link $DEST_REPO#$child_new under $DEST_REPO#$parent_new"
        summary "  - WARNING: could not link #$child_new under #$parent_new"
      fi
    fi
    copy_sub_issues "$child" "$child_new"
  done
}

for n in "$@"; do
  state="$(gh api "repos/$STARTER_REPO/issues/$n" --jq '.state' 2>/dev/null || true)"
  if [ "$state" != "open" ]; then
    log "ERROR: issue $n could not be retrieved as an open issue from $STARTER_REPO"
    summary "ERROR: issue $n could not be retrieved as an open issue from https://github.com/$STARTER_REPO"
    exit 1
  fi
  top_new="$(copy_issue "$n")"
  copy_sub_issues "$n" "$top_new"
done
