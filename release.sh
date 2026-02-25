#!/usr/bin/env bash
set -euo pipefail

BUMP=${1:-}
if [[ ! "$BUMP" =~ ^(patch|minor|major)$ ]]; then
  echo "Usage: $0 <patch|minor|major>" >&2
  exit 1
fi

CHART="app/helm/Chart.yaml"
POM="pom.xml"

CURRENT=$(grep '^version:' "$CHART" | awk '{print $2}')
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT"

case "$BUMP" in
  major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
  patch) PATCH=$((PATCH + 1)) ;;
esac

NEW="$MAJOR.$MINOR.$PATCH"

sed -i "s/^version: .*/version: $NEW/" "$CHART"
sed -i "s/^appVersion: .*/appVersion: \"$NEW\"/" "$CHART"
sed -i "s|<version>$CURRENT</version>|<version>$NEW</version>|" "$POM"

echo "Bumped $CURRENT → $NEW"

git add "$CHART" "$POM"
git commit -m "release v$NEW"
git tag "v$NEW"
git push
git push origin "v$NEW"
