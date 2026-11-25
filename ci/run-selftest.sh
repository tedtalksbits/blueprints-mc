#!/usr/bin/env bash
# Runs the Blueprints real-server self-test on a throwaway Paper server and fails unless it ends "SELFTEST PASSED".
# Used by CI (.github/workflows/ci.yml) and by hand on Linux or Git Bash.
#
#   ci/run-selftest.sh <paper.jar> <work-dir>
#
# Build first (`./gradlew build`): it writes server-plugin/build/libs/server-plugin-*.jar and
# paper-selftest/build/libs/paper-selftest-*.jar. <work-dir> must not exist yet (every run starts from a fresh world
# and a fresh config.yml). Offline, set PAPER_SEED_DIR to an existing server folder: its cache/, versions/ and
# libraries/ are copied in so Paper doesn't need to download the vanilla jar. To check imports too, set
# SELFTEST_LITEMATICS to a folder of .litematic files (otherwise that step reports SKIP).
#
# Environment: SELFTEST_TIMEOUT (seconds, default 600), SELFTEST_PORT (default 25599), JAVA (default java).
set -euo pipefail

paper_jar=${1:?usage: run-selftest.sh <paper.jar> <work-dir>}
work=${2:?usage: run-selftest.sh <paper.jar> <work-dir>}
timeout_s=${SELFTEST_TIMEOUT:-600}
port=${SELFTEST_PORT:-25599}
java=${JAVA:-java}
repo=$(cd "$(dirname "$0")/.." && pwd)

die() { echo "selftest: $*" >&2; exit 1; }

[ -f "$paper_jar" ] || die "no Paper jar at $paper_jar"
[ -e "$work" ] && die "$work already exists; give a new folder so the test starts from a fresh world"

shopt -s nullglob
plugin_jars=("$repo"/server-plugin/build/libs/server-plugin-*.jar)
test_jars=("$repo"/paper-selftest/build/libs/paper-selftest-*.jar)
shopt -u nullglob
[ ${#plugin_jars[@]} -eq 1 ] || die "expected one server-plugin jar in server-plugin/build/libs (found ${#plugin_jars[@]}); delete old ones and run ./gradlew build"
[ ${#test_jars[@]} -eq 1 ] || die "expected one paper-selftest jar in paper-selftest/build/libs (found ${#test_jars[@]}); delete old ones and run ./gradlew build"

mkdir -p "$work/plugins"
cp "$paper_jar" "$work/paper.jar"
cp "${plugin_jars[0]}" "${test_jars[0]}" "$work/plugins/"
if [ -n "${PAPER_SEED_DIR:-}" ]; then
    for d in cache versions libraries; do
        [ -d "$PAPER_SEED_DIR/$d" ] && cp -r "$PAPER_SEED_DIR/$d" "$work/"
    done
fi
if [ -n "${SELFTEST_LITEMATICS:-}" ]; then
    mkdir -p "$work/plugins/BlueprintsSelfTest/litematics"
    cp "$SELFTEST_LITEMATICS"/*.litematic "$work/plugins/BlueprintsSelfTest/litematics/"
fi

echo "eula=true" > "$work/eula.txt"
# Flat world: the self-test builds its water pools high above flat ground. Peaceful: slimes spawn in flat worlds.
cat > "$work/server.properties" <<PROPS
online-mode=false
level-type=minecraft\:flat
generate-structures=false
difficulty=peaceful
spawn-protection=0
server-port=$port
max-players=1
view-distance=4
simulation-distance=4
PROPS

echo "selftest: starting Paper ($(basename "$paper_jar")) with $(basename "${plugin_jars[0]}") in $work"
status=0
# stdin from /dev/null: nobody types into the console. The self-test plugin stops the server when it's done.
(cd "$work" && timeout --kill-after=30 "$timeout_s" "$java" -Xms1G -Xmx3G -Dblueprints.selftest.exit=true \
    -jar paper.jar --nogui < /dev/null > server.log 2>&1) || status=$?

report="$work/plugins/BlueprintsSelfTest/report.txt"
summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '%s\n' "$@" >> "$GITHUB_STEP_SUMMARY" || true; }

if [ ! -f "$report" ]; then
    [ $status -eq 124 ] && echo "selftest: Paper did not stop within ${timeout_s}s" >&2
    echo "selftest: no report was written. Last lines of server.log:" >&2
    tail -n 60 "$work/server.log" >&2 || true
    summary "### Blueprints self-test" "No report: the server stopped or hung before the self-test finished (exit $status)."
    exit 1
fi

# The report is long (every supported block is listed); the summary page gets everything but the PASS lines.
grep -v '^PASS ' "$report" || true
echo "selftest: $(grep -c '^PASS ' "$report") checks passed"
summary "### Blueprints self-test" '```' "$(grep -v '^PASS ' "$report")" "$(grep -c '^PASS ' "$report") checks passed" '```'

last=$(grep -v '^[[:space:]]*$' "$report" | tail -n 1)
case "$last" in
    "SELFTEST PASSED"*) echo "selftest: passed"; exit 0 ;;
    *) echo "selftest: failed ($last)" >&2; exit 1 ;;
esac
