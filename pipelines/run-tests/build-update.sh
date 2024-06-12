#!/bin/bash

template='{"key": "%s", "state": "%s", "name": "%s", "url": "%s", "description": "%s"}'

DATA=$(printf "$template" "$BUILD_ID" "$2" "$JOB_NAME" "$BUILD_URL" "$3")

URI='https://api.bitbucket.org/2.0/repositories'
OWNER='koodu_software'
REPO_SLUG=$1
REVISION=$GIT_COMMIT
URL="$URI/$OWNER/$REPO_SLUG/commit/$REVISION/statuses/build"

USER="koodu_bot"
PASSWORD="koodu"

echo $URL
echo $DATA
curl -s -u "$USER:$PASSWORD" $URL --header "Content-Type: application/json" --request POST --data "$DATA"
