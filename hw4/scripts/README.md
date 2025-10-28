# Helper Scripts

This directory contains helper scripts for managing the distributed file system.

## Startup Scripts

### Replication Mode

```bash
# Start controller on port 8000 (default)
./scripts/start-replication-controller.sh

# Start controller on custom port
./scripts/start-replication-controller.sh 9000

# Start chunk server
./scripts/start-replication-chunkserver.sh localhost 8000

# Start client
./scripts/start-replication-client.sh localhost 8000
```

### Erasure Coding Mode

```bash
# Start controller on port 8000 (default)
./scripts/start-erasure-controller.sh

# Start controller on custom port
./scripts/start-erasure-controller.sh 9000

# Start chunk server
./scripts/start-erasure-chunkserver.sh localhost 8000

# Start client
./scripts/start-erasure-client.sh localhost 8000
```

## Utility Scripts

### Format Code

```bash
# Check and apply code formatting with Spotless
./scripts/format-code.sh
```

### Cleanup

```bash
# Remove build artifacts, chunk server storage, and test data
./scripts/cleanup.sh
```

### Test System

```bash
# Get instructions for testing the system
./scripts/test-system.sh replication
./scripts/test-system.sh erasure
```

## Quick Start

1. **Start a local test cluster (replication mode):**
   ```bash
   # Terminal 1: Controller
   ./scripts/start-replication-controller.sh 8000

   # Terminal 2, 3, 4: Chunk Servers
   ./scripts/start-replication-chunkserver.sh localhost 8000
   ./scripts/start-replication-chunkserver.sh localhost 8000
   ./scripts/start-replication-chunkserver.sh localhost 8000

   # Terminal 5: Client
   ./scripts/start-replication-client.sh localhost 8000
   ```

2. **Test upload/download:**
   ```
   > upload ./test-file.txt /test/file.txt
   > download /test/file.txt ./downloaded.txt
   > exit
   ```

3. **Cleanup after testing:**
   ```bash
   ./scripts/cleanup.sh
   ```

## Notes

- All scripts automatically build the project before running
- Controller defaults to port 8000
- Chunk servers connect to localhost:8000 by default
- Chunk server storage is in `/tmp/chunk-server/`
