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

forbidden_core=(
  'org.springframework.boot:spring-boot-gradle-plugin'
  'org.graalvm.buildtools:native-gradle-plugin'
  'org.jooq:jooq-codegen-gradle'
  'org.jsonschema2pojo:jsonschema2pojo-gradle-plugin'
  'com.android.tools.build:gradle'
)

for coordinate in "${forbidden_core[@]}"; do
  if contains_coordinate "$core_dependencies" "$coordinate"; then
    echo "SimpleDSL core dependency isolation violation: ${coordinate}" >&2
    exit 1
  fi
done

if contains_coordinate "$java_dependencies" 'com.android.tools.build:gradle'; then
  echo 'SimpleDSL Java dependency isolation violation: com.android.tools.build:gradle' >&2
  exit 1
fi

required_core='io.github.qigao.simpledsl:simpledsl-core'
if ! contains_coordinate "$java_dependencies" "$required_core"; then
  echo "SimpleDSL Java publication must depend on ${required_core}" >&2
  echo 'Actual Java dependencies:' >&2
  printf '%s\n' "$java_dependencies" >&2
  exit 1
fi

echo 'SimpleDSL backend artifact isolation: OK'
