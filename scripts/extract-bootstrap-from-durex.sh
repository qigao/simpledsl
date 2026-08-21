#!/usr/bin/env bash
set -euo pipefail

source_root="${1:?source qigao/durex checkout is required}"
source_dir="$source_root/build-bootstrap/src/main/groovy/com/github/durex/gradle"
target_dir="simpledsl-build-bootstrap/src/main/groovy/io/github/qigao/simpledsl/gradle"

rm -rf "$target_dir/manifest" "$target_dir/settings"
mkdir -p "$target_dir/manifest" "$target_dir/settings"

cp "$source_dir"/manifest/*.groovy "$target_dir/manifest/"
for file in \
  DurexDependenciesTask.groovy \
  DurexProjectsTask.groovy \
  DurexSettingsExtension.groovy \
  DurexSettingsPlugin.groovy \
  ProjectDiscovery.groovy \
  ProjectRegistry.groovy \
  ProjectSpec.groovy; do
  cp "$source_dir/settings/$file" "$target_dir/settings/$file"
done

mv "$target_dir/settings/DurexDependenciesTask.groovy" "$target_dir/settings/SimpleDslDependenciesTask.groovy"
mv "$target_dir/settings/DurexProjectsTask.groovy" "$target_dir/settings/SimpleDslProjectsTask.groovy"
mv "$target_dir/settings/DurexSettingsExtension.groovy" "$target_dir/settings/SimpleDslSettingsExtension.groovy"
mv "$target_dir/settings/DurexSettingsPlugin.groovy" "$target_dir/settings/SimpleDslSettingsPlugin.groovy"

while IFS= read -r -d '' file; do
  sed -i \
    -e 's/com\.github\.durex\.gradle/io.github.qigao.simpledsl.gradle/g' \
    -e 's/DurexDependenciesTask/SimpleDslDependenciesTask/g' \
    -e 's/DurexProjectsTask/SimpleDslProjectsTask/g' \
    -e 's/DurexSettingsExtension/SimpleDslSettingsExtension/g' \
    -e 's/DurexSettingsPlugin/SimpleDslSettingsPlugin/g' \
    -e 's/durexDependencyRegistry/simpledslDependencyRegistry/g' \
    -e 's/durexSettings/simpledslSettings/g' \
    -e 's/durexDependencies/simpledslDependencies/g' \
    -e 's/durexProjects/simpledslProjects/g' \
    -e 's#gradle/dependencies/durex\.toml#gradle/simpledsl/dependencies.toml#g' \
    -e 's#gradle/modules\.toml#gradle/simpledsl/modules.toml#g' \
    -e 's/Durex/SimpleDSL/g' \
    "$file"
done < <(find "$target_dir" -type f -name '*.groovy' -print0)

echo "Extracted SimpleDSL bootstrap source from $source_root"
