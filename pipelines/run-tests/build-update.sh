#!/bin/bash

# Parameters
REPO_SLUG=$1
REVISION=$2
BUILD_ID=$3
STATE=$4
JOB_NAME=$5
BUILD_URL=$6
DESCRIPTION=$7
OWNER=$8
USER=$9
TOKEN=${10}

# Template for JSON data
template='{"key": "%s", "state": "%s", "name": "%s", "url": "%s", "description": "%s"}'

# Formatted JSON data
DATA=$(printf "$template" "$BUILD_ID" "$STATE" "$JOB_NAME #$BUILD_ID" "$BUILD_URL" "$DESCRIPTION")

# Bitbucket API URL
URI='https://api.bitbucket.org/2.0/repositories'
URL="$URI/$OWNER/$REPO_SLUG/commit/$REVISION/statuses/build"

# Print URL and DATA for debugging purposes
echo "$URL"
echo "$DATA"

# cURL request to update the build status on Bitbucket
curl -u "$USER:$TOKEN" "$URL" --header "Content-Type: application/json" --data "$DATA"