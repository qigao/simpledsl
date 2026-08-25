#!/usr/bin/env bash
set -euo pipefail

roots=(
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle
  simpledsl-core
  simpledsl-java
  simpledsl-android
  integration-tests-java
  integration-tests-android
  README.md
  CHANGELOG.md
)

existing=()
for path in "${roots[@]}"; do
  [[ -e "$path" ]] && existing+=("$path")
done

if ((${#existing[@]} > 0)); then
  if grep -RInE 'com\.github\.durex|\bdurex\.(settings|module|feature|schema|java-library|spring-library|spring-service)|Durex(Configuration|Extension|Module|Capability|Doctor|Settings|Dependency|Registry|Json|Jooq)' "${existing[@]}"; then
    echo 'legacy Durex product namespace leaked into SimpleDSL product files' >&2
    exit 1
  fi

  if grep -RInE '(^|[^[:alnum:]_.])simpledsl\.(settings|module|feature|schema|java-library|spring-library|spring-service)' "${existing[@]}"; then
    echo 'bare SimpleDSL public plugin id found; use io.github.qigao.simpledsl.*' >&2
    exit 1
  fi
fi

plugin_builds=(
  simpledsl-core/build.gradle.kts
  simpledsl-java/build.gradle.kts
  simpledsl-android/build.gradle.kts
)
if grep -nE 'tags[[:space:]]*=.*"(gradle|plugin)"' "${plugin_builds[@]}"; then
  echo 'Gradle Plugin Portal reserved tag found; gradle and plugin are forbidden tags' >&2
  exit 1
fi
