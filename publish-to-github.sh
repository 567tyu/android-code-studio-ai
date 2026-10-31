#!/usr/bin/env bash
#
# Publish the Android Code Studio AI fork to GitHub using the environment's
# pre-configured github.com credential (no PAT argument needed).
#
#   - Source code  -> pushed into the repo (the "source area").
#   - Built APKs   -> uploaded as a GitHub Release asset (the "download area"),
#     so they are NOT committed into the source tree.
#
# Usage:
#   ./publish-to-github.sh <owner> <repo> [branch]
#
# The credential token is read from `git credential fill` for github.com, is
# only used for this run, and is never written to disk or echoed.

set -euo pipefail
cd "$(dirname "$0")"

OWNER="${1:?usage: $0 <owner> <repo> [branch]}"
REPO="${2:?usage: $0 <owner> <repo> [branch]}"
BRANCH="${3:-main}"

REPO_FULL="$OWNER/$REPO"
API="https://api.github.com"
REMOTE_URL="https://github.com/$REPO_FULL.git"

# --- obtain the github.com credential (token) without printing it ----------
CRED="$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null)"
GH_PASS="$(printf '%s\n' "$CRED" | awk -F= '/^password=/{print $2}')"
[ -n "$GH_PASS" ] || { echo "no github.com credential found"; exit 1; }

apk_files=(
  "core/app/build/outputs/apk/debug/android_code_studio_ai-arm64-v8a-debug-1.0.0+gh.r04.apk"
  "core/app/build/outputs/apk/debug/android_code_studio_ai-armeabi-v7a-debug-1.0.0+gh.r04.apk"
)
for f in "${apk_files[@]}"; do
  [ -f "$f" ] || { echo "Missing APK: $f (run ./build.sh build first)"; exit 1; }
done

# --- verify the token can access this repo ---------------------------------
echo "==> Checking access to $REPO_FULL ..."
if ! curl -fsSL -o /dev/null -H "Authorization: token $GH_PASS" "$API/repos/$REPO_FULL"; then
  echo "Cannot access $REPO_FULL with the environment credential. Confirm the repo name (owner/name)."
  exit 1
fi

# --- revert build churn, keep local-only items out of the published repo ---
echo "==> Preparing source commit ..."
git checkout -- external/acsprovider/build 2>/dev/null || true
{
  echo ".monkeycode/"
  echo "signing/local-debug-key.jks"
  echo "external/acsprovider/build/"
  echo "*.log"
} >> .git/info/exclude
git add -A
git commit -q -m "chore: Android Code Studio AI fork (packaging, domestic mirrors, OpenAI-compatible API, GitHub auto-push)"

# --- push source -----------------------------------------------------------
echo "==> Pushing source to $REMOTE_URL (branch $BRANCH) ..."
git push -f "https://x-access-token:$GH_PASS@github.com/$REPO_FULL.git" "HEAD:refs/heads/$BRANCH"

# --- create a Release and upload the APKs as assets (download area) -------
TAG="ai-build-$(date +%Y%m%d-%H%M%S)"
echo "==> Creating GitHub release '$TAG' and uploading APKs ..."
release_json="$(curl -fsSL -X POST \
  -H "Authorization: token $GH_PASS" -H "Accept: application/vnd.github+json" \
  "$API/repos/$REPO_FULL/releases" \
  -d "{\"tag_name\":\"$TAG\",\"name\":\"Android Code Studio AI build\",\"draft\":false,\"prerelease\":true}")"
upload_url="$(printf '%s' "$release_json" | python3 -c 'import sys,json;print(json.load(sys.stdin)["upload_url"])')"
# upload_url ends with ...assets{?name,label}; strip the placeholder, add our own ?name=
upload_base="${upload_url%{?name,label}}"
upload_base="${upload_base%{?name}}"

for f in "${apk_files[@]}"; do
  name="$(basename "$f")"
  enc_name="$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$name")"
  echo "    uploading $name ..."
  curl -fsSL -X POST \
    -H "Authorization: token $GH_PASS" \
    -H "Content-Type: application/octet-stream" \
    "${upload_base}?name=${enc_name}" \
    --data-binary "@$f" > /dev/null
done

echo
echo "==> Done."
echo "    Source:  $REMOTE_URL (branch $BRANCH)"
echo "    Release: $API/repos/$REPO_FULL/releases/tag/$TAG"
