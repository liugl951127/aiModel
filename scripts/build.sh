#!/usr/bin/env bash
# Build all backend services into fat-jars.
# Usage: ./scripts/build.sh
set -e

cd "$(dirname "$0")/../backend"

echo "==> Installing parent + common"
mvn install -N -DskipTests -q
mvn install -pl ai-platform-common -DskipTests -q

echo "==> Packaging services"
mvn package -DskipTests

echo "==> Done. JARs are in each module's target/."
