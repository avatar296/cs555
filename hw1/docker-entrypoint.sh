#!/bin/bash
set -e

# Apply JAVA_OPTS if set
if [ -n "$JAVA_OPTS" ]; then
    export JAVA_TOOL_OPTIONS="$JAVA_OPTS"
fi

# Function to wait for a service to be available
wait_for_service() {
    local host=$1
    local port=$2
    local max_attempts=30
    local attempt=0
    
    echo "Waiting for $host:$port to be available..."
    
    while [ $attempt -lt $max_attempts ]; do
        if nc -z "$host" "$port" 2>/dev/null; then
            echo "$host:$port is available"
            return 0
        fi
        
        attempt=$((attempt + 1))
        echo "Attempt $attempt/$max_attempts: $host:$port not yet available"
        sleep 2
    done
    
    echo "Failed to connect to $host:$port after $max_attempts attempts"
    return 1
}

# Determine the node type and start appropriately
if [ "$NODE_TYPE" = "REGISTRY" ]; then
    echo "Starting Registry node on port ${REGISTRY_PORT:-8080}"
    exec java csx55.overlay.node.Registry "${REGISTRY_PORT:-8080}"
    
elif [ "$NODE_TYPE" = "MESSAGING_NODE" ]; then
    # Wait for registry to be available
    wait_for_service "${REGISTRY_HOST:-registry}" "${REGISTRY_PORT:-8080}"
    
    # Add a small delay to ensure registry is fully initialized
    sleep 2
    
    echo "Starting Messaging Node connecting to ${REGISTRY_HOST:-registry}:${REGISTRY_PORT:-8080}"
    exec java csx55.overlay.node.MessagingNode "${REGISTRY_HOST:-registry}" "${REGISTRY_PORT:-8080}"
    
else
    # Default: execute whatever command was passed
    exec "$@"
fi