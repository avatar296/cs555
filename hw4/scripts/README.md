# Testing Scripts for CS555 HW4

Simple scripts to test the distributed file system.

## Scripts

### `cleanup.sh`
Clean up all test data and kill running processes.

```bash
./scripts/cleanup.sh
```

### `controller.sh`
Start the Controller node.

```bash
./scripts/controller.sh [port]

# Examples:
./scripts/controller.sh          # Start on port 8000 (default)
./scripts/controller.sh 9000     # Start on port 9000
```

### `chunkserver.sh`
Start a ChunkServer.

```bash
./scripts/chunkserver.sh [controller-host] [controller-port]

# Examples:
./scripts/chunkserver.sh                    # Connect to localhost:8000
./scripts/chunkserver.sh localhost 9000     # Connect to localhost:9000
```

## Testing Workflow

### Quick Test (Upload a file)

**Terminal 1 - Start Controller:**
```bash
./scripts/controller.sh 8000
```

**Terminal 2 - Start ChunkServer 1:**
```bash
./scripts/chunkserver.sh localhost 8000
```

**Terminal 3 - Start ChunkServer 2:**
```bash
./scripts/chunkserver.sh localhost 8000
```

**Terminal 4 - Start ChunkServer 3:**
```bash
./scripts/chunkserver.sh localhost 8000
```

**Terminal 5 - Create test file and run client:**
```bash
# Create test file
echo "This is a test file for CS555 HW4" > /tmp/test.txt

# Start client
./gradlew runReplicationClient -PappArgs="localhost 8000" --console=plain

# In client prompt:
> upload /tmp/test.txt /test/myfile.txt
```

**Expected output:**
- File size and number of chunks
- 3 lines of `<ip>:<port>` (the 3 replicas)
- "Upload completed successfully"

**Verify storage:**
```bash
find /tmp/chunk-server -name "*myfile.txt*"
# Should show 3 copies of /test/myfile.txt_chunk1
```

### Cleanup
```bash
./scripts/cleanup.sh
```

## Notes

- Scripts can be run from any directory (they auto-navigate to project root)
- Controller must be started before ChunkServers
- Wait a few seconds after starting ChunkServers for them to register
- Use Ctrl+C to stop any component
