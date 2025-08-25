#!/bin/bash

echo "--- Building the project ---"
./gradlew build

if [ $? -ne 0 ]; then
  echo "Gradle build failed. Exiting."
  exit 1
fi

echo "--- Starting the Registry and Messaging Nodes ---"

REGISTRY_PORT=8080

osascript -e 'tell application "Terminal" to do script "cd '$(pwd)' && ./gradlew runRegistry --args=\"'$REGISTRY_PORT'\""'

sleep 2

osascript -e 'tell application "Terminal" to do script "cd '$(pwd)' && ./gradlew runMessagingNode --args=\"localhost '$REGISTRY_PORT'\""'

osascript -e 'tell application "Terminal" to do script "cd '$(pwd)' && ./gradlew runMessagingNode --args=\"localhost '$REGISTRY_PORT'\""'

echo "--- System started in new terminal tabs ---"