#!/usr/bin/env sh
set -eu

TAG="${1:-latest}"
OUTPUT_DIR="${OUTPUT_DIR:-docker-images}"
NO_SAVE="${NO_SAVE:-0}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required. Please install Docker Engine first." >&2
  exit 1
fi

cd "$(dirname "$0")/.."

echo "Building sushijia-hotel:${TAG}"
docker build -t "sushijia-hotel:${TAG}" -f sushijia-server/Dockerfile --build-arg MODULE=sushijia-hotel sushijia-server

echo "Building sushijia-admin:${TAG}"
docker build -t "sushijia-admin:${TAG}" -f sushijia-server/Dockerfile --build-arg MODULE=sushijia-admin sushijia-server

echo "Building sushijia-web:${TAG}"
docker build -t "sushijia-web:${TAG}" -f Dockerfile .

if [ "$NO_SAVE" != "1" ]; then
  mkdir -p "$OUTPUT_DIR"
  echo "Saving image tar files to ${OUTPUT_DIR}"
  docker save -o "${OUTPUT_DIR}/sushijia-hotel-${TAG}.tar" "sushijia-hotel:${TAG}"
  docker save -o "${OUTPUT_DIR}/sushijia-admin-${TAG}.tar" "sushijia-admin:${TAG}"
  docker save -o "${OUTPUT_DIR}/sushijia-web-${TAG}.tar" "sushijia-web:${TAG}"
fi

echo "Done."
