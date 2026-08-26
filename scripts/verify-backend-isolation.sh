#!/usr/bin/env bash
set -euo pipefail

repository="${1:?test repository path is required}"
version="${2:?SimpleDSL version is required}"

pom_for() {
  local artifact="$1"
  local pom
  pom="$(find "$repository" -type f -name '*.pom' -path "*/${artifact}/${version}/*" -print -quit)"
  if [[ -z "$pom" ]]; then
    echo "missing published POM for io.github.qigao.simpledsl:${artifact}:${version}" >&2
    return 1
  fi
  printf '%s\n' "$pom"
}

dependencies_for() {
  local artifact="$1"
  local pom
  pom="$(pom_for "$artifact")"
  awk '
    /<dependency>/ {
      in_dependency = 1
      group_id = ""
      artifact_id = ""
    }
    in_dependency && /<groupId>/ {
      value = $0
      sub(/^.*<groupId>/, "", value)
      sub(/<\/groupId>.*$/, "", value)
      group_id = value
    }
    in_dependency && /<artifactId>/ {
      value = $0
      sub(/^.*<artifactId>/, "", value)
      sub(/<\/artifactId>.*$/, "", value)
      artifact_id = value
    }
    /<\/dependency>/ && in_dependency {
      if (group_id != "" && artifact_id != "") {
        print group_id ":" artifact_id
      }
      in_dependency = 0
    }
  ' "$pom"
}

contains_coordinate() {
  local dependencies="$1"
  local coordinate="$2"
  grep -Fxq "$coordinate" <<< "$dependencies"
}

core_dependencies="$(dependencies_for simpledsl-core)"
java_dependencies="$(dependencies_for simpledsl-java)"
android_dependencies="$(dependencies_for simpledsl-android)"

java_tooling=(
  'org.springframework.boot:spring-boot-gradle-plugin'
  'org.graalvm.buildtools:native-gradle-plugin'
  'org.jooq:jooq-codegen-gradle'
  'org.jsonschema2pojo:jsonschema2pojo-gradle-plugin'
)
compose_compiler_tooling='org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin'
ksp_tooling='com.google.devtools.ksp:symbol-processing-gradle-plugin'

for coordinate in "${java_tooling[@]}" 'com.android.tools.build:gradle' "$compose_compiler_tooling" "$ksp_tooling"; do
  if contains_coordinate "$core_dependencies" "$coordinate"; then
    echo "SimpleDSL core dependency isolation violation: ${coordinate}" >&2
    exit 1
  fi
done

for coordinate in 'com.android.tools.build:gradle' "$compose_compiler_tooling" "$ksp_tooling"; do
  if contains_coordinate "$java_dependencies" "$coordinate"; then
    echo "SimpleDSL Java dependency isolation violation: ${coordinate}" >&2
    exit 1
  fi
done
if contains_coordinate "$java_dependencies" 'io.github.qigao.simpledsl:simpledsl-android'; then
  echo 'SimpleDSL Java dependency isolation violation: simpledsl-android' >&2
  exit 1
fi

for coordinate in "${java_tooling[@]}"; do
  if contains_coordinate "$android_dependencies" "$coordinate"; then
    echo "SimpleDSL Android dependency isolation violation: ${coordinate}" >&2
    exit 1
  fi
done
if contains_coordinate "$android_dependencies" 'io.github.qigao.simpledsl:simpledsl-java'; then
  echo 'SimpleDSL Android dependency isolation violation: simpledsl-java' >&2
  exit 1
fi

required_core='io.github.qigao.simpledsl:simpledsl-core'
if ! contains_coordinate "$java_dependencies" "$required_core"; then
  echo "SimpleDSL Java publication must depend on ${required_core}" >&2
  echo 'Actual Java dependencies:' >&2
  printf '%s\n' "$java_dependencies" >&2
  exit 1
fi

if ! contains_coordinate "$android_dependencies" "$required_core"; then
  echo "SimpleDSL Android publication must depend on ${required_core}" >&2
  echo 'Actual Android dependencies:' >&2
  printf '%s\n' "$android_dependencies" >&2
  exit 1
fi

required_agp='com.android.tools.build:gradle'
if ! contains_coordinate "$android_dependencies" "$required_agp"; then
  echo "SimpleDSL Android publication must depend on ${required_agp}" >&2
  echo 'Actual Android dependencies:' >&2
  printf '%s\n' "$android_dependencies" >&2
  exit 1
fi

if ! contains_coordinate "$android_dependencies" "$compose_compiler_tooling"; then
  echo "SimpleDSL Android publication must depend on ${compose_compiler_tooling}" >&2
  echo 'Actual Android dependencies:' >&2
  printf '%s\n' "$android_dependencies" >&2
  exit 1
fi

if ! contains_coordinate "$android_dependencies" "$ksp_tooling"; then
  echo "SimpleDSL Android publication must depend on ${ksp_tooling}" >&2
  echo 'Actual Android dependencies:' >&2
  printf '%s\n' "$android_dependencies" >&2
  exit 1
fi

echo 'SimpleDSL backend artifact isolation: OK'
