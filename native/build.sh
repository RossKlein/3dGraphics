#!/usr/bin/env bash
# Build the rmlui_java native library and place it in natives/
# Run from the repo root or from native/

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build-linux"

echo "[rmlui-java] Configuring..."
cmake -S "$SCRIPT_DIR" -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE=Release

echo "[rmlui-java] Building..."
cmake --build "$BUILD_DIR" --parallel "$(nproc)"

echo "[rmlui-java] Done — librmlui_java.so written to natives/"
