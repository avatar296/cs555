package csx55.dfs.replication;

import java.io.*;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.*;

import csx55.dfs.base.BaseClient;
import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.IOUtils;

public class Client extends BaseClient {

    public Client(String controllerHost, int controllerPort) {
        super(controllerHost, controllerPort);
    }

    @Override
    protected String getClientType() {
        return "Client";
    }

    @Override
    protected void uploadFile(String sourcePath, String destPath) throws Exception {
        validateSourceFile(sourcePath);
        destPath = normalizePath(destPath);

        List<byte[]> chunks = readFileInChunks(sourcePath);
        long fileSize = new File(sourcePath).length();

        System.out.println("Uploading file: " + sourcePath + " -> " + destPath);
        System.out.println("File size: " + fileSize + " bytes, Chunks: " + chunks.size());

        List<String> allChunkServers = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            int chunkNumber = i + 1;
            byte[] chunkData = chunks.get(i);

            List<String> chunkServers = getChunkServersForWrite(destPath, chunkNumber, 3);

            if (chunkServers.size() != 3) {
                throw new IOException("Controller did not return 3 chunk servers");
            }

            writeChunkPipeline(destPath, chunkNumber, chunkData, chunkServers);

            allChunkServers.addAll(chunkServers);
        }

        for (String server : allChunkServers) {
            System.out.println(server);
        }

        System.out.println("Upload completed successfully");
    }

    @Override
    protected void downloadFile(String sourcePath, String destPath) throws Exception {
        sourcePath = normalizePath(sourcePath);

        System.out.println("Downloading file: " + sourcePath + " -> " + destPath);

        int numChunks = getFileChunkCount(sourcePath);

        if (numChunks == 0) {
            throw new FileNotFoundException("File not found: " + sourcePath);
        }

        ByteArrayOutputStream fileOutput = new ByteArrayOutputStream();
        List<String> chunkServersUsed = new ArrayList<>();
        List<String> corruptedChunks = new ArrayList<>();
        List<String> failedServers = new ArrayList<>();

        for (int i = 0; i < numChunks; i++) {
            int chunkNumber = i + 1;
            boolean chunkSucceeded = false;
            byte[] chunkData = null;

            for (int attempt = 0; attempt < 3; attempt++) {

                String chunkServer = getChunkServerForRead(sourcePath, chunkNumber);

                if (chunkServer == null) {
                    failedServers.add("Failed to locate chunk " + chunkNumber);
                    throw new IOException("Cannot locate chunk " + chunkNumber);
                }

                try {

                    chunkData = readChunkFromServer(chunkServer, sourcePath, chunkNumber);
                    chunkServersUsed.add(chunkServer);
                    chunkSucceeded = true;
                    break;

                } catch (Exception e) {

                    if (e.getMessage() != null && e.getMessage().contains("corruption")) {

                        String errorMsg = e.getMessage();
                        int sliceNumber = -1;

                        if (errorMsg.contains(" slice ")) {
                            try {
                                String sliceStr =
                                        errorMsg.substring(errorMsg.indexOf(" slice ") + 7);
                                sliceNumber = Integer.parseInt(sliceStr.trim());
                            } catch (Exception parseEx) {

                            }
                        }

                        String corruptionMsg =
                                chunkServer
                                        + " "
                                        + chunkNumber
                                        + " "
                                        + sliceNumber
                                        + " is corrupted";
                        corruptedChunks.add(corruptionMsg);

                        if (attempt == 2) {
                            throw new IOException("All replicas failed for chunk " + chunkNumber);
                        }

                    } else {

                        failedServers.add(chunkServer + " has failed");
                        throw e;
                    }
                }
            }

            if (chunkSucceeded && chunkData != null) {
                fileOutput.write(chunkData);
            }
        }

        for (String failure : failedServers) {
            System.out.println(failure);
        }

        for (String corruption : corruptedChunks) {
            System.out.println(corruption);
        }

        for (String server : chunkServersUsed) {
            System.out.println(server);
        }

        IOUtils.writeWithDirectoryCreation(Paths.get(destPath), fileOutput.toByteArray());

        System.out.println("Download completed successfully");
    }

    private void writeChunkPipeline(
            String filename, int chunkNumber, byte[] data, List<String> chunkServers)
            throws Exception {
        TCPConnection.Address addr = TCPConnection.Address.parse(chunkServers.get(0));

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            List<String> nextServers =
                    new ArrayList<>(chunkServers.subList(1, chunkServers.size()));
            StoreChunkRequest request =
                    new StoreChunkRequest(filename, chunkNumber, data, nextServers);

            connection.sendMessage(request);
            connection.receiveMessage();
        }
    }

    private String getChunkServerForRead(String filename, int chunkNumber)
            throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(controllerHost, controllerPort);
                TCPConnection connection = new TCPConnection(socket)) {

            ChunkServerForReadRequest request =
                    new ChunkServerForReadRequest(filename, chunkNumber);
            connection.sendMessage(request);

            Message response = connection.receiveMessage();
            return ((ChunkServerForReadResponse) response).getChunkServer();
        }
    }

    private byte[] readChunkFromServer(String chunkServer, String filename, int chunkNumber)
            throws Exception {
        TCPConnection.Address addr = TCPConnection.Address.parse(chunkServer);

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            ReadChunkRequest request = new ReadChunkRequest(filename, chunkNumber);
            connection.sendMessage(request);

            Message response = connection.receiveMessage();
            ChunkDataResponse dataResponse = (ChunkDataResponse) response;

            if (!dataResponse.isSuccess()) {
                throw new IOException(dataResponse.getErrorMessage());
            }

            return dataResponse.getData();
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println(
                    "Usage: java csx55.dfs.replication.Client <controller-ip> <controller-port>");
            System.exit(1);
        }

        try {
            String controllerHost = args[0];
            int controllerPort = Integer.parseInt(args[1]);

            Client client = new Client(controllerHost, controllerPort);
            client.start();
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid integer");
            System.exit(1);
        }
    }
}
