# CI

GitHub Actions, `.github/workflows/ci.yml`. Runs on every push to `main`, on pull requests, and by hand (Actions tab >
CI > Run workflow). Pushes that only touch Markdown, `docs/` or `run/` are skipped. Nothing is deployed anywhere.

| Job | Does | Fails when |
|---|---|---|
| Plugin build and unit tests | `./gradlew build` at the root: plugin, self-test plugin, MockBukkit unit tests | Compile error or a unit test fails |
| Self-test on a real Paper server | Downloads the pinned Paper build, checks its SHA-256, runs `ci/run-selftest.sh` | The report doesn't end `SELFTEST PASSED`, or the server hangs (10 minutes) |
| Client build and unit tests | `fabric-client/gradlew build`: the Fabric mod and its unit tests | Compile error (usually a Minecraft GUI or render class moved) or a test fails |
| Release (tags `v*` only) | All of the above, then publishes both jars (see Releases) | Any CI job fails, or the tag doesn't match the plugin version |

Each run keeps the jars as artifacts (`server-plugin`, `paper-selftest`, `blueprints-client`) plus `selftest-report`
(report and server log). The self-test's findings (everything but its PASS lines) are printed on the run's summary
page.

The self-test (`paper-selftest/`) checks on a real server what MockBukkit can't: plugin channels, the building
palette, rotation and mirroring, exact placement, and building into water (every supported block in real water,
against a dry control). The `.litematic` import step needs files and reports `SKIP` in CI.

## Pins

The workflow's `env` block: `JAVA_VERSION`, `PAPER_VERSION`, `PAPER_BUILD`, `PAPER_SHA256`. Build 211 is the same jar
as the local test server's `run/local-paper/paper-26.3-211.jar`. To move to a new Paper build, take the hash of the
jar you tested locally (`certutil -hashfile paper-<v>-<b>.jar SHA256` on Windows, `sha256sum` elsewhere) and change
all three Paper values together (`docs/UPGRADING.md`).

## Running the self-test by hand

On Linux or in Git Bash, after `./gradlew build`:

```
ci/run-selftest.sh path/to/paper-26.3-211.jar /tmp/selftest-run
```

The second argument must be a folder that doesn't exist yet. Without internet access, add
`PAPER_SEED_DIR=path/to/an/existing/paper/server` so its `cache/`, `versions/` and `libraries/` are reused. To include
the import check, add `SELFTEST_LITEMATICS=path/to/folder` with `.litematic` files. The script stops if
`server-plugin/build/libs` holds more than one jar (an old version left behind); delete the old one.

## Dependabot

`.github/dependabot.yml` checks the workflows' actions monthly and opens a PR when one has a new version; CI runs on
the PR, so merge it when it's green. It doesn't touch Gradle dependencies: Paper, MockBukkit and Fabric move with the
Minecraft version (`docs/UPGRADING.md`).

## Releases

`.github/workflows/release.yml`: the tag is the **plugin** version, and the client keeps its own version. Both jars go
in every release, and the notes name both versions.

1. Bump what changed: `version` in `build.gradle.kts` (plugin) and/or `version` in `fabric-client/gradle.properties`
   (client). A release always needs a new plugin version, since that's the tag.
2. Commit and push to `main`; wait for CI to go green.
3. Tag and push: `git tag v0.6.0` then `git push origin v0.6.0`.

The release workflow runs all of CI again (builds, unit tests, real-Paper self-test), checks that the tag matches the
plugin version and that both jars exist, then publishes "Blueprints v0.6.0" on the Releases page with
`server-plugin-<plugin>.jar` and `blueprints-client-<client>.jar`. If anything fails, nothing is published; fix it,
delete the tag (`git push origin :v0.6.0`, `git tag -d v0.6.0`) and tag again. The self-test plugin is never released.

## Not yet

The real client is not launched in CI; UI checks stay manual (`docs/TEST_PLAN.md`).
