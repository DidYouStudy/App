#!/usr/bin/env bash
# Co-authored-by: OpenAI Codex
# Restore and validate the required team debug key without exposing its contents in logs.
set -euo pipefail

destination="${1:?Pass the destination keystore path}"
if [[ -z "${DEBUG_KEYSTORE_BASE64:-}" ]]; then
  echo '::error::DEBUG_KEYSTORE_BASE64 is required for debug signing.' >&2
  exit 1
fi

umask 077
temporary_key="$(mktemp)"
trap 'rm -f "$temporary_key"' EXIT
printf '%s' "$DEBUG_KEYSTORE_BASE64" | base64 --decode > "$temporary_key"
# Match the debug signing configuration and reject invalid files before replacing any key.
keytool -list -keystore "$temporary_key" -storepass android -alias androiddebugkey > /dev/null 2>&1
mkdir -p "$(dirname "$destination")"
cp "$temporary_key" "$destination"
chmod 600 "$destination"
echo 'Private debug key restored.'
