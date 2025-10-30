package csx55.dfs.base;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.*;

import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.DFSConfig;
import csx55.dfs.util.PathUtil;

public abstract class BaseClient {

    protected final String controllerHost;
    protected final int controllerPort;

    public BaseClient(String controllerHost, int controllerPort) {
        this.controllerHost = controllerHost;
        this.controllerPort = controllerPort;
    }

    protected abstract String getClientType();

    protected abstract void uploadFile(String sourcePath, String destPath) throws Exception;

    protected abstract void downloadFile(String sourcePath, String destPath) throws Exception;

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

    protected int getFileChunkCount(String filename) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            FileInfoRequest request = new FileInfoRequest(filename);
            connection.sendMessage(request);

            Message response = connection.receiveMessage();
            return ((FileInfoResponse) response).getNumChunks();
        }
    }

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

    protected String normalizePath(String path) {
        return PathUtil.normalize(path);
    }

    protected void validateSourceFile(String sourcePath) throws FileNotFoundException {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Source file not found: " + sourcePath);
        }
    }

    protected List<byte[]> readFileInChunks(String sourcePath) throws IOException {
        File sourceFile = new File(sourcePath);
        byte[] fileData = Files.readAllBytes(sourceFile.toPath());
        int numChunks = (int) Math.ceil((double) fileData.length / DFSConfig.CHUNK_SIZE);

        List<byte[]> chunks = new ArrayList<>();
        for (int i = 0; i < numChunks; i++) {
            int offset = i * DFSConfig.CHUNK_SIZE;
            int length = Math.min(DFSConfig.CHUNK_SIZE, fileData.length - offset);
            chunks.add(Arrays.copyOfRange(fileData, offset, offset + length));
        }

        return chunks;
    }

    protected Message sendToController(Message request) throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(request);
            return connection.receiveMessage();
        }
    }

    protected Message sendToChunkServer(String serverAddress, Message request)
            throws IOException, ClassNotFoundException {
        TCPConnection.Address addr = TCPConnection.Address.parse(serverAddress);

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(request);
            return connection.receiveMessage();
        }
    }

    protected void validateChunkDataResponse(ChunkDataResponse response) throws IOException {
        if (!response.isSuccess()) {
            throw new IOException("Chunk operation failed: " + response.getErrorMessage());
        }
    }

    protected void requireResponseType(Message response, MessageType expectedType)
            throws IOException {
        if (response.getType() != expectedType) {
            throw new IOException(
                    "Unexpected response type: expected "
                            + expectedType
                            + ", got "
                            + response.getType());
        }
    }
}
