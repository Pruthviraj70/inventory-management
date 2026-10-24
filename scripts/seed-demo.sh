#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if (( $# > 1 )) || { (( $# == 1 )) && [[ "$1" != --dry-run && "$1" != --verify ]]; }; then
  echo 'Usage: ./scripts/seed-demo.sh [--dry-run|--verify]' >&2
  exit 2
fi
# Configuration is loaded by Database; the script never reads or prints credentials.
mvn -q compile exec:java -Dexec.mainClass=com.pruthviraj.inventory.demo.DemoSeeder "-Dexec.args=${1:-}"
