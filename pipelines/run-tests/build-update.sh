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

# Function to display error message and exit
display_error() {
  echo "***********************"
  echo "BUILD-UPDATE Error"
  echo "Check: $1"
  echo "***********************"
  echo "Parameters received:"
  echo "REPO_SLUG: ${REPO_SLUG:-EMPTY}"
  echo "REVISION: ${REVISION:-EMPTY}"
  echo "BUILD_ID: ${BUILD_ID:-EMPTY}"
  echo "STATE: ${STATE:-EMPTY}"
  echo "JOB_NAME: ${JOB_NAME:-EMPTY}"
  echo "BUILD_URL: ${BUILD_URL:-EMPTY}"
  echo "DESCRIPTION: ${DESCRIPTION:-EMPTY}"
  echo "OWNER: ${OWNER:-EMPTY}"
  echo "USER: ${USER:-EMPTY}"
  echo "TOKEN: ${TOKEN:-EMPTY}"
  echo "***********************"
  exit 1
}

# Check for empty parameters and exit with an error message if any are found
if [ -z "$REPO_SLUG" ]; then display_error "REPO_SLUG is empty"; fi
if [ -z "$REVISION" ]; then display_error "REVISION is empty"; fi
if [ -z "$BUILD_ID" ]; then display_error "BUILD_ID is empty"; fi
if [ -z "$STATE" ]; then display_error "STATE is empty"; fi
if [ -z "$JOB_NAME" ]; then display_error "JOB_NAME is empty"; fi
if [ -z "$BUILD_URL" ]; then display_error "BUILD_URL is empty"; fi
if [ -z "$DESCRIPTION" ]; then display_error "DESCRIPTION is empty"; fi
if [ -z "$OWNER" ]; then display_error "OWNER is empty"; fi
if [ -z "$USER" ]; then display_error "USER is empty"; fi
if [ -z "$TOKEN" ]; then display_error "TOKEN is empty"; fi

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
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -u "$USER:$TOKEN" "$URL" --header "Content-Type: application/json" --data "$DATA")

# Check if the response code is not 20x
if [[ ! "$RESPONSE" =~ ^20[0-9]$ ]]; then
  echo "***********************"
  echo "BUILD-UPDATE Error"
  echo "Failed to update build status on Bitbucket. HTTP status code: $RESPONSE"
  echo "***********************"
  echo "Parameters received:"
  echo "REPO_SLUG: $REPO_SLUG"
  echo "REVISION: $REVISION"
  echo "BUILD_ID: $BUILD_ID"
  echo "STATE: $STATE"
  echo "JOB_NAME: $JOB_NAME"
  echo "BUILD_URL: $BUILD_URL"
  echo "DESCRIPTION: $DESCRIPTION"
  echo "OWNER: $OWNER"
  echo "USER: $USER"
  echo "TOKEN: $TOKEN"
  echo "***********************"
  exit 1
fi