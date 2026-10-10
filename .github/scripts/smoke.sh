#!/usr/bin/env bash
set -euo pipefail

: "${IMAGE:?}" "${TYPE:?}"
container=bridge-smoke
log=server.log
startup_line='No Discord bot token is set'
bridge_name='minecraft[ -]?discord[ -]?bridge'

case "$TYPE" in
  PAPER | FOLIA | PURPUR | LEAF) mount=/plugins pattern="minecraft-discord-bridge-paper-*.jar" ;;
  VELOCITY) mount=/plugins pattern="minecraft-discord-bridge-velocity-*.jar" ;;
  *) mount=/mods pattern="minecraft-discord-bridge-${TYPE,,}-*+${JAR_MINECRAFT:-$VERSION}.jar" ;;
esac

jar=$(find jars -type f -name "$pattern" -print -quit)
if [[ -z "$jar" ]]; then
  echo "::error::No jar in the build matches $pattern"
  exit 1
fi
mkdir -p addons
cp "$jar" addons/
echo "Testing $(basename "$jar") on $TYPE ${VERSION:-latest} with $IMAGE"

args=(--name "$container" -e EULA=TRUE -e TYPE="$TYPE" -e MEMORY=2G -v "$PWD/addons:$mount:ro")
if [[ -n "${VERSION:-}" ]]; then
  args+=(-e VERSION="$VERSION")
fi
for pair in ${SERVER_ENV:-}; do
  args+=(-e "$pair")
done
docker run -d --quiet "${args[@]}" "$IMAGE" > /dev/null

save_log() {
  docker logs "$container" > "$log" 2>&1 || true
}

logged() {
  save_log
  grep -qF "$1" "$log"
}

fail() {
  save_log
  echo "::error::$1"
  tail -n 80 "$log"
  exit 1
}

deadline=$((SECONDS + ${BOOT_TIMEOUT:-900}))
until logged 'Done ('; do
  [[ "$(docker inspect -f '{{.State.Running}}' "$container")" == true ]] || fail "The server exited before it finished starting."
  ((SECONDS < deadline)) || fail "The server did not finish starting in time."
  sleep 5
done

deadline=$((SECONDS + 60))
until logged "$startup_line"; do
  ((SECONDS < deadline)) || fail "The bridge did not log \"$startup_line\" after the server started."
  sleep 2
done

started=$SECONDS
docker stop -t 120 "$container" > /dev/null
stop_seconds=$((SECONDS - started))
exit_code=$(docker inspect -f '{{.State.ExitCode}}' "$container")
save_log
echo "The server stopped in ${stop_seconds}s with exit code $exit_code."

status=0
if grep -iE "$bridge_name" "$log" | grep -E '(^|[^A-Za-z])ERROR([^A-Za-z]|$)'; then
  echo "::error::The log has ERROR lines that mention the bridge."
  status=1
fi
if grep -E '^[[:space:]]+at com\.minecraftdiscordbridge\.' "$log"; then
  echo "::error::The log has a stack trace that runs through the bridge."
  status=1
fi
if [[ "$exit_code" == 137 ]]; then
  echo "::error::The server did not stop on its own and was killed."
  status=1
fi
exit "$status"
