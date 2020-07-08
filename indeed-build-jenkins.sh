#!/bin/bash
set -eu

rm -Rf build/distributions

# print stacktrace and info by default so pipeline build failures are easier to debug
./gradlew --stacktrace --info clean binZip -P finalRelease
