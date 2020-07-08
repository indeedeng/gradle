#!/bin/bash
set -eu

rm -Rf build/distributions

./gradlew clean binZip -P finalRelease
