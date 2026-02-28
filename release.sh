#!/usr/bin/env bash
set -euo pipefail

BUMP=${1:-}
if [[ ! "$BUMP" =~ ^(patch|minor|major)$ ]]; then
  echo "Usage: $0 <patch|minor|major>" >&2
  exit 1
fi

# Ensure we're on main
BRANCH=$(git symbolic-ref --short HEAD)
if [[ "$BRANCH" != "main" ]]; then
  echo "Error: releases must be made from main (currently on '$BRANCH')" >&2
  exit 1
fi

# Ensure local main is in sync with origin/main
git fetch origin main --quiet
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/main)
if [[ "$LOCAL" != "$REMOTE" ]]; then
  echo "Error: local main is out of sync with origin/main" >&2
  echo "  local:  $LOCAL" >&2
  echo "  remote: $REMOTE" >&2
  exit 1
fi

CHART="app/helm/Chart.yaml"

CURRENT=$(grep '^version:' "$CHART" | awk '{print $2}')
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT"

case "$BUMP" in
  major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
  patch) PATCH=$((PATCH + 1)) ;;
esac

NEW="$MAJOR.$MINOR.$PATCH"

# Ensure tag doesn't already exist
if git rev-parse "v$NEW" &>/dev/null 2>&1; then
  echo "Error: tag 'v$NEW' already exists" >&2
  exit 1
fi

sed -i "s/^version: .*/version: $NEW/" "$CHART"
sed -i "s/^appVersion: .*/appVersion: \"$NEW\"/" "$CHART"
for POM in pom.xml core/pom.xml rest/pom.xml app/pom.xml; do
  sed -i "s|<version>$CURRENT</version>|<version>$NEW</version>|g" "$POM"
done

echo "Bumping $CURRENT → $NEW"

echo "Updating $CHART and pom.xml files..."
echo "Committing..."
git add "$CHART" pom.xml core/pom.xml rest/pom.xml app/pom.xml
git commit -m "release v$NEW" -q
echo "Tagging v$NEW..."
git tag "v$NEW"
echo "Pushing branch..."
git push -q
echo "Pushing tag..."
git push origin "v$NEW" -q
echo "Done: released v$NEW"
