#!/usr/bin/env bash
set -euo pipefail

source_repo=${1:?source Durex checkout is required}
source_root="$source_repo/build-logic/src/main/groovy/com/github/durex/gradle"
target_root="simpledsl-build-logic/src/main/groovy/io/github/qigao/simpledsl/gradle"

rm -rf "$target_root"
mkdir -p "$target_root"

cp "$source_root/DurexConfigurationException.groovy" "$target_root/"
cp "$source_root/DurexDependencyAccess.groovy" "$target_root/"
cp "$source_root/DurexExtension.groovy" "$target_root/"
cp "$source_root/DurexModulePlugin.groovy" "$target_root/"
cp "$source_root/ModuleKind.groovy" "$target_root/"
cp "$source_root/PersistenceExtension.groovy" "$target_root/"
cp -R "$source_root/capability" "$target_root/"
cp -R "$source_root/catalog" "$target_root/"
cp -R "$source_root/dependency" "$target_root/"
cp -R "$source_root/diagnostics" "$target_root/"
cp -R "$source_root/model" "$target_root/"

mv "$target_root/DurexConfigurationException.groovy" "$target_root/SimpleDslConfigurationException.groovy"
mv "$target_root/DurexDependencyAccess.groovy" "$target_root/SimpleDslDependencyAccess.groovy"
mv "$target_root/DurexExtension.groovy" "$target_root/SimpleDslExtension.groovy"
mv "$target_root/DurexModulePlugin.groovy" "$target_root/SimpleDslModulePlugin.groovy"
mv "$target_root/PersistenceExtension.groovy" "$target_root/SimpleDslPersistenceExtension.groovy"
mv "$target_root/capability/DurexCapabilitySupport.groovy" "$target_root/capability/SimpleDslCapabilitySupport.groovy"
mv "$target_root/catalog/DurexCatalogPlugin.groovy" "$target_root/catalog/SimpleDslCatalogPlugin.groovy"
mv "$target_root/catalog/DurexRegistryBridge.groovy" "$target_root/catalog/SimpleDslRegistryBridge.groovy"
mv "$target_root/diagnostics/DurexCapabilitiesTask.groovy" "$target_root/diagnostics/SimpleDslCapabilitiesTask.groovy"
mv "$target_root/diagnostics/DurexDoctorTask.groovy" "$target_root/diagnostics/SimpleDslDoctorTask.groovy"
mv "$target_root/diagnostics/DurexDoctorValidator.groovy" "$target_root/diagnostics/SimpleDslDoctorValidator.groovy"
mv "$target_root/model/DurexModuleModel.groovy" "$target_root/model/SimpleDslModuleModel.groovy"

find "$target_root" -type f -name '*.groovy' -print0 | xargs -0 perl -pi -e '
  s/com\.github\.durex\.gradle/io.github.qigao.simpledsl.gradle/g;
  s/DurexConfigurationException/SimpleDslConfigurationException/g;
  s/DurexDependencyAccess/SimpleDslDependencyAccess/g;
  s/DurexExtension/SimpleDslExtension/g;
  s/DurexModulePlugin/SimpleDslModulePlugin/g;
  s/PersistenceExtension/SimpleDslPersistenceExtension/g;
  s/DurexCapabilitySupport/SimpleDslCapabilitySupport/g;
  s/DurexCatalogPlugin/SimpleDslCatalogPlugin/g;
  s/DurexRegistryBridge/SimpleDslRegistryBridge/g;
  s/DurexCapabilitiesTask/SimpleDslCapabilitiesTask/g;
  s/DurexDoctorTask/SimpleDslDoctorTask/g;
  s/DurexDoctorValidator/SimpleDslDoctorValidator/g;
  s/DurexModuleModel/SimpleDslModuleModel/g;
  s/durex/simpledsl/g;
  s/Durex/SimpleDSL/g;
'

if grep -RInE 'com\.github\.durex|\bdurex\.|Durex' "$target_root"; then
  echo 'legacy Durex namespace remains in extracted build logic core' >&2
  exit 1
fi
