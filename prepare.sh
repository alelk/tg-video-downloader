#!/usr/bin/env bash
# Called by semantic-release verifyReleaseCmd.
# Writes the next version to app.version.
set -euo pipefail

NEXT_RELEASE_VERSION="$(cat .nextRelease.txt)"

if [[ -z "${NEXT_RELEASE_VERSION}" ]]; then
  echo "ERROR: .nextRelease.txt is empty"
  exit 1
fi

echo "${NEXT_RELEASE_VERSION}" > app.version
echo "✅  Prepared app.version: ${NEXT_RELEASE_VERSION}"

