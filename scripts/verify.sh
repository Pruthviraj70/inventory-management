#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B -ntp test-compile
mvn -q exec:java -Dexec.mainClass=com.pruthviraj.inventory.PureChecks -Dexec.classpathScope=test
if [[ "${1:-}" == "--database" || "${1:-}" == "--gui" ]]; then
  mvn -q exec:java -Dexec.mainClass=com.pruthviraj.inventory.DatabaseChecks -Dexec.classpathScope=test
  mvn -q exec:java -Dexec.mainClass=com.pruthviraj.inventory.PersistenceCheck -Dexec.classpathScope=test
fi
if [[ "${1:-}" == "--gui" ]]; then
  mvn -q exec:java -Dexec.mainClass=com.pruthviraj.inventory.ui.GuiChecks -Dexec.classpathScope=test
fi
