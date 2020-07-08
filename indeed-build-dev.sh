#!/bin/bash
set -eu

rm -Rf build/distributions

./gradlew binZip

rm -Rf build/extracted
mkdir build/extracted
unzip -q build/distributions/gradle-*-bin.zip -d build/extracted
rm -Rf build/out
mv build/extracted/gradle-* build/out
rm -R build/extracted build/distributions

echo "Built into the ./build/out folder"
echo "Run export GRADLE_DEV_DIR=$(pwd)/build/out to use the Gradle built locally."
