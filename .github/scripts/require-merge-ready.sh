#!/usr/bin/env bash
set -euo pipefail

draft_state="${PR_DRAFT:-${1:-}}"

case "$draft_state" in
  false)
    echo "The pull request is merge-ready; canonical quality may run."
    ;;
  true)
    echo "::error::Draft pull requests must not run or satisfy canonical quality."
    exit 1
    ;;
  *)
    echo "::error::Missing or invalid pull-request draft state."
    exit 1
    ;;
esac
