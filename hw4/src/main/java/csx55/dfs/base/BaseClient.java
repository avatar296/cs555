/* CS555 Distributed Systems - HW4 */
package csx55.dfs.base;

import java.io.*;
import java.net.Socket;
import java.util.*;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.PathUtil;

/**
 * Abstract base class for Client implementations. Provides common functionality for both erasure
 * coding and replication modes.
 */
public abstract class BaseClient {

    protected final String controllerHost;
    protected final int controllerPort;

    public BaseClient(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
    }

    /** Returns the client type for logging purposes. */
    protected abstract String getClientType();

    /** Uploads a file to the distributed file system. */
    protected abstract void uploadFile(String sourcePath, String destPath) throws Exception;

    /** Downloads a file from the distributed file system. */
    protected abstract void downloadFile(String sourcePath, String destPath) throws Exception;

    /** Starts the interactive client shell. */
    public void start() {
        System.out.println(
                getClientType()
                        + " connected to Controller: "
                        + controllerHost
                        + ":"
                        + controllerPort);
        System.out.println("Commands:");
        System.out.println("  upload <source> <destination>");
        System.out.println("  download <source> <destination>");
        System.out.println("  exit");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "upload":
                        if (parts.length != 3) {
                            System.err.println("Usage: upload <source> <destination>");
                        } else {
                            uploadFile(parts[1], parts[2]);
                        }
                        break;

                    case "download":
                        if (parts.length != 3) {
                            System.err.println("Usage: download <source> <destination>");
                        } else {
                            downloadFile(parts[1], parts[2]);
                        }
                        break;

                    case "exit":
                        System.out.println("Exiting...");
                        scanner.close();
                        return;

                    default:
                        System.err.println("Unknown command: " + command);
                        break;
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /** Gets the number of chunks for a file from the controller. */
    protected int getFileChunkCount(String filename) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            FileInfoRequest request = new FileInfoRequest(filename);
            connection.sendMessage(request);

            Message response = connection.receiveMessage();
            return ((FileInfoResponse) response).getNumChunks();
        }
    }

    /** Gets chunk servers for writing from the controller. */
    protected List<String> getChunkServersForWrite(String filename, int chunkNumber, int count)
            throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            ChunkServersRequest request = new ChunkServersRequest(filename, chunkNumber, count);
            connection.sendMessage(request);

            Message response = connection.receiveMessage();
            return ((ChunkServersResponse) response).getChunkServers();
        }
    }

    /** Normalizes a path for the distributed file system. */
    protected String normalizePath(String path) {
        return PathUtil.normalize(path);
    }

    /** Validates that a source file exists. */
    protected void validateSourceFile(String sourcePath) throws FileNotFoundException {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Source file not found: " + sourcePath);
        }
    }

    /** Sends a message to the controller and returns the response. */
    protected Message sendToController(Message request) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(request);
            return connection.receiveMessage();
        }
    }

    /** Sends a message to a chunk server and returns the response. */
    protected Message sendToChunkServer(String serverAddress, Message request)
            throws IOException, ClassNotFoundException {
        TCPConnection.Address addr = TCPConnection.Address.parse(serverAddress);

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(request);
            return connection.receiveMessage();
        }
    }
}
