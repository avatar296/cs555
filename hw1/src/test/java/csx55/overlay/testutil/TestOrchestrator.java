package csx55.overlay.testutil;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates the lifecycle of Registry and MessagingNode processes for
 * testing
 */
public class TestOrchestrator {

    private Process registryProcess;
    private final List<Process> messagingNodeProcesses = new ArrayList<>();
    private final Map<Process, BufferedReader> outputReaders = new HashMap<>();
    private final Map<Process, BufferedWriter> inputWriters = new HashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final List<String> registryOutput = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, List<String>> nodeOutputs = new ConcurrentHashMap<>();

    private static final int STARTUP_DELAY_MS = 2000;
    private static final int COMMAND_DELAY_MS = 500;

    /**
     * Start the Registry process
     */
    public void startRegistry(int port) throws IOException, InterruptedException {
        // Kill any process using this port first
        killProcessOnPort(port);
        
        String classpath = getClasspath();
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-cp", classpath,
                "csx55.overlay.node.Registry",
                String.valueOf(port));

        // Properly redirect streams to allow command input
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        
        registryProcess = pb.start();
        setupProcessIO(registryProcess, registryOutput);
        
        // Small delay to let process start and output be captured
        Thread.sleep(100);
        
        // Wait for registry to actually start listening
        long startTime = System.currentTimeMillis();
        boolean started = false;
        while (System.currentTimeMillis() - startTime < STARTUP_DELAY_MS * 2) {
            for (String line : registryOutput) {
                if (line.contains("Registry listening on port") || 
                    line.contains("Started Registry") ||
                    line.contains("Registry started")) {
                    started = true;
                    break;
                }
            }
            if (started) break;
            
            // Also check if process is still alive
            if (!registryProcess.isAlive()) {
                // Try to get exit code for debugging
                int exitCode = registryProcess.exitValue();
                System.err.println("Registry process died with exit code: " + exitCode);
                System.err.println("Registry output:");
                for (String line : registryOutput) {
                    System.err.println("  " + line);
                }
                throw new IOException("Registry process died with exit code: " + exitCode);
            }
            
            Thread.sleep(100);
        }
        
        if (!started) {
            // Print any output we got for debugging
            System.err.println("Registry output captured:");
            for (String line : registryOutput) {
                System.err.println("  " + line);
            }
            throw new IOException("Registry failed to start on port " + port + " - no confirmation message received");
        }
        
        System.out.println("[TestOrchestrator] Registry started on port " + port);
    }

    /**
     * Start a MessagingNode process
     */
    public int startMessagingNode(String registryHost, int registryPort) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-cp", getClasspath(),
                "csx55.overlay.node.MessagingNode",
                registryHost,
                String.valueOf(registryPort));

        // Properly redirect streams to allow command input
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        
        Process nodeProcess = pb.start();
        int nodeId = messagingNodeProcesses.size();
        messagingNodeProcesses.add(nodeProcess);

        List<String> nodeOutput = Collections.synchronizedList(new ArrayList<>());
        nodeOutputs.put(nodeId, nodeOutput);
        setupProcessIO(nodeProcess, nodeOutput);

        // Wait for node to register
        long startTime = System.currentTimeMillis();
        boolean registered = false;
        while (System.currentTimeMillis() - startTime < STARTUP_DELAY_MS * 2) {
            for (String line : nodeOutput) {
                if (line.contains("Registration request successful") || 
                    line.contains("registered successfully") ||
                    line.contains("Registered with Registry")) {
                    registered = true;
                    break;
                }
            }
            if (registered) break;
            Thread.sleep(100);
        }
        
        if (!registered) {
            // Still allow it to continue - some nodes might not output registration success
            System.out.println("[TestOrchestrator] Warning: MessagingNode " + nodeId + " registration not confirmed");
        }
        
        System.out.println("[TestOrchestrator] MessagingNode " + nodeId + " started");
        return nodeId;
    }

    /**
     * Send command to Registry
     */
    public void sendRegistryCommand(String command) throws IOException, InterruptedException {
        sendCommand(registryProcess, command);
        Thread.sleep(COMMAND_DELAY_MS);
    }

    /**
     * Send command to specific MessagingNode
     */
    public void sendNodeCommand(int nodeId, String command) throws IOException, InterruptedException {
        if (nodeId < messagingNodeProcesses.size()) {
            sendCommand(messagingNodeProcesses.get(nodeId), command);
            Thread.sleep(COMMAND_DELAY_MS);
        }
    }

    /**
     * Get Registry output
     */
    public List<String> getRegistryOutput() {
        return new ArrayList<>(registryOutput);
    }

    /**
     * Get specific node output
     */
    public List<String> getNodeOutput(int nodeId) {
        return new ArrayList<>(nodeOutputs.getOrDefault(nodeId, new ArrayList<>()));
    }

    /**
     * Wait for specific output from Registry
     */
    public boolean waitForRegistryOutput(String expectedOutput, int timeoutSeconds) {
        return waitForOutput(registryOutput, expectedOutput, timeoutSeconds);
    }

    /**
     * Wait for specific output from MessagingNode
     */
    public boolean waitForNodeOutput(int nodeId, String expectedOutput, int timeoutSeconds) {
        List<String> output = nodeOutputs.get(nodeId);
        if (output == null)
            return false;
        return waitForOutput(output, expectedOutput, timeoutSeconds);
    }

    /**
     * Shutdown all processes
     */
    public void shutdown() {
        System.out.println("[TestOrchestrator] Shutting down all processes");

        // Shutdown messaging nodes
        for (Process node : messagingNodeProcesses) {
            if (node != null && node.isAlive()) {
                node.destroyForcibly();
            }
        }

        // Shutdown registry
        if (registryProcess != null && registryProcess.isAlive()) {
            registryProcess.destroyForcibly();
        }

        // Shutdown executor
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("[TestOrchestrator] Executor service did not terminate");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get the number of active MessagingNodes
     */
    public int getNodeCount() {
        return messagingNodeProcesses.size();
    }

    // Private helper methods

    private void killProcessOnPort(int port) {
        try {
            // Use lsof to find the process using the port
            ProcessBuilder pb = new ProcessBuilder("lsof", "-t", "-i:" + port);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String pid = reader.readLine();
            
            if (pid != null && !pid.isEmpty()) {
                System.out.println("Found process " + pid + " using port " + port + ", killing it...");
                ProcessBuilder killPb = new ProcessBuilder("kill", "-9", pid.trim());
                Process killProcess = killPb.start();
                killProcess.waitFor(1, TimeUnit.SECONDS);
                Thread.sleep(500); // Give it time to release the port
                System.out.println("Killed process " + pid + " that was using port " + port);
            }
        } catch (Exception e) {
            // Ignore errors - port might not be in use
            System.out.println("No process found using port " + port + " or unable to kill: " + e.getMessage());
        }
    }

    private String getClasspath() {
        String cp = System.getProperty("java.class.path");
        // Add build output directories if not present
        if (!cp.contains("build/classes/java/main")) {
            // Use absolute path
            String projectDir = System.getProperty("user.dir");
            cp = projectDir + "/build/classes/java/main:" + cp;
        }
        return cp;
    }

    private void setupProcessIO(Process process, List<String> outputList) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        outputReaders.put(process, reader);
        inputWriters.put(process, writer);

        // Start output capture thread
        executorService.execute(() -> {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    outputList.add(line);
                    System.out.println("[Process Output] " + line);
                }
            } catch (IOException e) {
                // Process terminated
            }
        });

        // Capture error stream - also add to output list for startup detection
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        executorService.execute(() -> {
            String line;
            try {
                while ((line = errorReader.readLine()) != null) {
                    outputList.add(line); // Add to output list for checking
                    System.err.println("[Process Error] " + line);
                }
            } catch (IOException e) {
                // Process terminated
            }
        });
    }

    private void sendCommand(Process process, String command) throws IOException {
        if (!process.isAlive()) {
            throw new IOException("Process is not alive, cannot send command: " + command);
        }
        
        BufferedWriter writer = inputWriters.get(process);
        if (writer != null) {
            try {
                writer.write(command);
                writer.newLine();
                writer.flush();
                System.out.println("[Command Sent] " + command);
            } catch (IOException e) {
                if (!process.isAlive()) {
                    throw new IOException("Process died while sending command: " + command, e);
                }
                throw e;
            }
        } else {
            throw new IOException("No writer available for process");
        }
    }

    private boolean waitForOutput(List<String> outputList, String expectedOutput, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            for (String line : outputList) {
                if (line.contains(expectedOutput)) {
                    return true;
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * Clear all captured output
     */
    public void clearOutputs() {
        registryOutput.clear();
        nodeOutputs.values().forEach(List::clear);
    }

    /**
     * Get last N lines from Registry output
     */
    public List<String> getLastRegistryOutput(int n) {
        List<String> output = getRegistryOutput();
        int start = Math.max(0, output.size() - n);
        return output.subList(start, output.size());
    }
}