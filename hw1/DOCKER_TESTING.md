# Docker Testing Instructions for CS555 HW1 Overlay Network

## Overview
This guide explains how to use Docker containers to test your overlay network implementation with interactive command access to the Registry and MessagingNode applications.

## Container Setup

### Current Configuration
- **Main compose**: `docker-compose.yml` - Auto-starts Java applications with `stdin_open: true`
- **Override compose**: `docker-compose.override.yml` - Starts containers with bash shells for manual control

### Quick Start Commands

```bash
# Clean slate start
docker-compose down -v

# Start all containers (uses override - bash shells)
docker-compose up -d

# Check container status
docker-compose ps
```

## Testing Workflow

### Method 1: Manual Application Startup (Recommended)

1. **Start Registry**:
   ```bash
   # Access registry container
   docker-compose exec registry bash
   
   # Inside container, start Registry application
   java -cp /app csx55.overlay.node.Registry 8080
   ```

2. **Start MessagingNodes** (in separate terminals):
   ```bash
   # Terminal 2: Node 1
   docker-compose exec node-1 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   
   # Terminal 3: Node 2
   docker-compose exec node-2 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   
   # Continue for as many nodes as needed (up to node-10)
   ```

3. **Issue Commands**:
   - Registry commands go to the Registry terminal
   - Node commands go to individual node terminals

### Method 2: Auto-Started Applications

```bash
# Remove override file temporarily
mv docker-compose.override.yml docker-compose.override.yml.bak

# Start with auto-started Java apps
docker-compose up -d

# Access registry (Java app already running)
docker-compose exec registry bash
# Commands should work directly since stdin_open: true

# Restore override when done
mv docker-compose.override.yml.bak docker-compose.override.yml
```

## Command Reference

### Registry Commands

Execute these in the Registry container after starting the Java application:

| Command | Description | Example |
|---------|-------------|---------|
| `list-messaging-nodes` | Show all registered nodes | Lists IP:port of registered nodes |
| `setup-overlay <CR>` | Create overlay with CR connections per node | `setup-overlay 4` |
| `list-weights` | Display overlay links with weights | Shows all link weights |
| `send-overlay-link-weights` | Broadcast link weights to all nodes | Sends weights after setup |
| `start <rounds>` | Begin message exchange | `start 5` (25 messages per node) |

**Example Registry Session**:
```bash
# After starting: java -cp /app csx55.overlay.node.Registry 8080
list-messaging-nodes
setup-overlay 4
send-overlay-link-weights  
start 5
```

### MessagingNode Commands

Execute these in individual node containers:

| Command | Description | Notes |
|---------|-------------|-------|
| `print-mst` | Display MST from this node's perspective | Shows BFS order from root |
| `exit-overlay` | Deregister and exit cleanly | Cleans up connections |

**Example Node Session**:
```bash
# After starting: java -cp /app csx55.overlay.node.MessagingNode registry 8080
print-mst
# ... (wait for message rounds to complete)
exit-overlay
```

## Complete Testing Scenario

### Scenario: 10-Node Overlay with 4 Connections Each

1. **Prepare Environment**:
   ```bash
   docker-compose down -v
   docker-compose up -d
   docker-compose ps  # Verify all containers running
   ```

2. **Start Registry** (Terminal 1):
   ```bash
   docker-compose exec registry bash
   java -cp /app csx55.overlay.node.Registry 8080
   ```

3. **Start 10 MessagingNodes** (Terminals 2-11):
   ```bash
   # Repeat for node-1 through node-10
   docker-compose exec node-1 bash
   java -cp /app csx55.overlay.node.MessagingNode registry 8080
   ```

4. **Execute Registry Commands** (in Registry terminal):
   ```bash
   list-messaging-nodes      # Should show 10 nodes
   setup-overlay 4           # Creates 4 connections per node
   list-weights              # Shows all link weights
   send-overlay-link-weights # Broadcasts to all nodes
   start 5                   # Initiates 5 rounds (25 messages per node)
   ```

5. **Test Node Commands** (in any node terminal):
   ```bash
   print-mst    # Shows MST from this node's perspective
   ```

6. **Verify Results**:
   - Registry should show traffic summary after `start` command
   - Total sent should equal total received across all nodes
   - Each node should show correct MST topology

## Troubleshooting

### Common Issues

**Issue**: `bash: list-messaging-nodes: command not found`
- **Cause**: Typing commands in bash shell instead of Java application
- **Solution**: Start Java app first: `java -cp /app csx55.overlay.node.Registry 8080`

**Issue**: `Connection refused` errors
- **Cause**: Applications not started in correct order
- **Solution**: Start Registry first, then nodes

**Issue**: `Error reading from null` / `EOFException`
- **Cause**: Connection issues between containers
- **Solution**: Ensure Docker network is properly configured

**Issue**: No response to commands
- **Cause**: Java application not reading from stdin properly
- **Solution**: Verify command loop implementation in Registry/MessagingNode

### Debug Commands

```bash
# Check container logs
docker-compose logs registry
docker-compose logs node-1

# Check network connectivity
docker-compose exec node-1 ping registry

# Check if Java processes are running
docker-compose exec registry ps aux | grep java

# Check port bindings
docker-compose exec registry netstat -tlnp
```

## File Structure

```
hw1/
├── docker-compose.yml              # Main compose with stdin_open: true
├── docker-compose.override.yml     # Override for bash shells
├── docker-compose.test.yml         # Original test configuration
├── Dockerfile                      # Container build instructions
└── DOCKER_TESTING.md              # This instruction file
```

## Development Tips

### Rapid Testing Cycle

1. **Make code changes** in your Java files
2. **Rebuild containers**: `docker-compose build`
3. **Restart containers**: `docker-compose down && docker-compose up -d`
4. **Test immediately** with manual startup

### Multiple Test Runs

```bash
# Quick restart for new test
docker-compose restart

# Clean restart (clears all state)
docker-compose down -v && docker-compose up -d
```

### Selective Container Management

```bash
# Restart just the registry
docker-compose restart registry

# Start only specific nodes
docker-compose up -d registry node-1 node-2 node-3
```

## Container Resource Usage

- **Memory**: Each container uses ~100-200MB
- **CPU**: Minimal during idle, spikes during message rounds
- **Network**: Uses custom bridge network `overlay-net`
- **Ports**: Only registry exposes port 8080 to host

## Expected Output Examples

### Successful Registration
```
[2025-09-04 22:30:15.123] [INFO] [Registry] Node registration successful: 172.18.0.3:45678
```

### Overlay Setup
```
setup completed with 20 connections
```

### Traffic Summary
```
172.18.0.3:45678 25 125 1250.00 625.00 15
172.18.0.4:45679 25 125 1300.00 650.00 12
...
sum 250 250 12500.00 6250.00 135
```

## Notes

- **Container Names**: Use overlay-registry, overlay-node-1, etc.
- **Network**: All containers communicate via overlay-net bridge
- **Persistence**: Use `-v` flag with `down` to clear volumes
- **Scaling**: Can easily test with fewer nodes by starting subset
- **Logs**: All Java application logs appear in container stdout