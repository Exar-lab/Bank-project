#!/usr/bin/env bash
set -euo pipefail

# Critical suite for local env parity with CI (Linux/WSL + Testcontainers)

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="$ROOT_DIR/banco-service"

TEST_CLASSES="RefreshTokenRepositoryIntegrationTest,UserCredentialRepositoryIntegrationTest"

if [[ ! -f "$MODULE_DIR/mvnw" ]]; then
  echo "❌ No se encontró mvnw en: $MODULE_DIR"
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "❌ Java no está disponible en PATH. Configurá Java 21 en WSL/Linux."
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "❌ Docker CLI no está disponible."
  exit 1
fi

echo "== Preflight =="
java -version
echo "JAVA_HOME=${JAVA_HOME:-<empty>}"

if ! docker info >/dev/null 2>&1; then
  echo "❌ docker info falló. Verificá Docker Desktop + WSL integration."
  exit 1
fi

# Detect the right Docker socket depending on host OS
OS="$(uname -s)"
case "$OS" in
  Linux*)
    # WSL2 or native Linux — use Unix socket
    if [[ ! -S /var/run/docker.sock ]]; then
      echo "❌ No existe /var/run/docker.sock."
      echo "   Activá Docker Desktop -> Settings -> Resources -> WSL Integration (Ubuntu)."
      exit 1
    fi
    export DOCKER_HOST="unix:///var/run/docker.sock"
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
    ;;
  MINGW*|MSYS*|CYGWIN*)
    # Windows Git Bash — Docker Desktop WSL2 backend uses dockerDesktopLinuxEngine pipe
    LINUX_ENGINE_PIPE="npipe:////./pipe/dockerDesktopLinuxEngine"
    LEGACY_PIPE="npipe:////./pipe/docker_engine"
    if docker -H "$LINUX_ENGINE_PIPE" info >/dev/null 2>&1; then
      export DOCKER_HOST="$LINUX_ENGINE_PIPE"
    elif docker -H "$LEGACY_PIPE" info >/dev/null 2>&1; then
      export DOCKER_HOST="$LEGACY_PIPE"
    else
      echo "❌ No se pudo conectar a Docker Desktop en ningún named pipe conocido."
      echo "   Asegurate de que Docker Desktop está corriendo y el engine WSL2 está activo."
      exit 1
    fi
    ;;
  *)
    echo "⚠️  OS no reconocido ($OS) — usando detección automática de Testcontainers"
    ;;
esac

echo "DOCKER_HOST=${DOCKER_HOST:-<auto-detect>}"

echo "== Running critical integration tests =="
cd "$MODULE_DIR"
./mvnw -Dtest="$TEST_CLASSES" test "$@"

echo "✅ Critical local suite completada."
