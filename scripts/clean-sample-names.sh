#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if (( $# > 1 )) || { (( $# == 1 )) && [[ "$1" != --dry-run ]]; }; then
  echo 'Usage: ./scripts/clean-sample-names.sh [--dry-run]' >&2
  exit 2
fi
mvn -q compile exec:java -Dexec.mainClass=com.pruthviraj.inventory.demo.SampleNameCleanup "-Dexec.args=${1:-}"
