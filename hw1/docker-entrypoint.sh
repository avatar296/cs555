#!/bin/bash
set -e

# This script will now just execute the command passed to it.
# The full java command with the correct classpath is now defined
# in the docker-compose files.

exec "$@"