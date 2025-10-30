package csx55.dfs.util;

import java.io.IOException;
import java.net.Socket;

import csx55.dfs.protocol.Message;
import csx55.dfs.transport.TCPConnection;

public class NetworkUtils {

    private NetworkUtils() {}

    public static Message sendRequestToServer(String serverAddress, Message request)
            throws IOException, ClassNotFoundException {
        TCPConnection.Address addr = TCPConnection.Address.parse(serverAddress);

        try (Socket socket = new Socket(addr.host, addr.port);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(request);
            return connection.receiveMessage();
        }
    }

    public static Message sendMessageAndWait(String host, int port, Message request)
            throws IOException, ClassNotFoundException {
        try (Socket socket = new Socket(host, port);
                TCPConnection connection = new TCPConnection(socket)) {

            connection.sendMessage(request);
            return connection.receiveMessage();
        }
    }

    public static Message sendRequestWithLogging(
            String serverAddress, Message request, String operationName) {
        try {
            return sendRequestToServer(serverAddress, request);
        } catch (Exception e) {
            System.err.println(
                    "Error during "
                            + operationName
                            + " to "
                            + serverAddress
                            + ": "
                            + e.getMessage());
            return null;
        }
    }
}
