#!/bin/bash
set -eu

VERSION=$(cat version.txt)

cd build/distributions

REPOSITORY_ID='dev-tools'
NEXUS_URL="https://nexus.corp.indeed.com/repository/${REPOSITORY_ID}"

FOLDER='gradle'

# The base name of the artifact
ARTIFACTS="gradle-${VERSION}-bin.zip"

curl -u "${nexus_username}:${nexus_password}" --upload-file ${ARTIFACTS} ${NEXUS_URL}/${FOLDER}/${ARTIFACTS}


