# Releasing prepper-backend

A release is a git tag. Pushing a tag matching `v*.*.*` triggers
[`.github/workflows/release.yml`](.github/workflows/release.yml), which builds and publishes
everything — nothing is published from a developer machine.

## Version numbering

One version number covers the whole project, and **six files must agree on it**:

- `app/helm/Chart.yaml` — both `version` and `appVersion`
- `pom.xml`, `core/pom.xml`, `rest/pom.xml`, `app/pom.xml`
- `rest/src/main/resources/openapi.yaml` — `info.version`

`Chart.yaml` is the reference: the release workflow reads `version` from it and **fails the build
if the pushed tag does not match**. A tag of `v0.11.0` against a chart still saying `0.10.0` aborts
before anything is published.

## Cutting a release

From a clean `main` that is in sync with `origin/main`:

```bash
release patch   # or: minor, major
```

`release` is a personal driver script living at `~/bin/release` — it is not part of this repo. It
enforces the preconditions (on `main`, synced with origin, tag not already taken), then calls this
repo's [`release-hooks`](release-hooks) script, which owns everything repo-specific:

| Hook | What it does |
|---|---|
| `get-version` | Reads the current version from `Chart.yaml` |
| `update <current> <new>` | Rewrites the version in all six files listed above |
| `stage` | `git add`s those files |

The driver then commits as `release v<new>`, tags `v<new>`, and pushes both the branch and the tag.

If you release without that script, do the same thing by hand: bump all six files, commit, tag
`v<version>`, and push the tag.

## What the tag triggers

`release.yml` runs on the pushed tag and:

1. Validates the tag against `Chart.yaml` (fails fast on a mismatch).
2. Runs the test suite — `./mvnw test -pl rest -am`.
3. Builds the app — `./mvnw package -pl app -am -DskipTests`.
4. Builds and pushes a multi-arch (amd64 + arm64) image from `app/src/main/docker/Dockerfile.jvm`
   to `ghcr.io/osbe/prepper-backend`, tagged with both the version and `latest`.
5. Packages and pushes the Helm chart to `oci://ghcr.io/osbe/charts`.
6. Creates a GitHub Release with auto-generated notes.

All of it authenticates with the workflow's `GITHUB_TOKEN`; no additional secrets are configured.

## After the release

Deploying is a separate, manual step — the workflow publishes artifacts but never touches a
cluster. To roll the new version onto the production cluster, bump the `backend:` version in the
`prepper-infra` repo's `helmfile.yaml` and apply it. See [DEPLOY.md](DEPLOY.md) for the deployment
paths.

## If a release fails

The tag is pushed before the workflow runs, so a failed release leaves a tag behind. Fix forward
with a new patch version rather than reusing the tag — `release` refuses a tag that already exists,
and the published image and chart for a given version should never change meaning.
