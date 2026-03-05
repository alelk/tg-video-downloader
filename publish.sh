#!/usr/bin/env bash
# Called by semantic-release publishCmd.
# This project does NOT publish JARs to GitHub Packages and does NOT
# push Docker images automatically — images are published manually
# via the workflow_dispatch workflow docker-publish.yml.
# This script only echoes the version for traceability.
set -euo pipefail

NEXT_RELEASE_VERSION="$(cat .nextRelease.txt)"

if [[ -z "${NEXT_RELEASE_VERSION}" ]]; then
  echo "ERROR: .nextRelease.txt is empty"
  exit 1
fi

echo "✅  Release version: ${NEXT_RELEASE_VERSION}"
echo "ℹ️   Docker images are published manually via docker-publish workflow."
echo "ℹ️   JARs are NOT published to any registry."

