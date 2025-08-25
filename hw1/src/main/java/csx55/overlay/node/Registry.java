package csx55.overlay.node;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Registry {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java csx55.overlay.node.Registry <port-number>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Registry listening on port: " + port);

            Socket clientSocket = serverSocket.accept();
            System.out.println("Accepted connection from: " + clientSocket.getInetAddress());

            // Streams
            DataInputStream din = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());

            // Registry request
            int messageType = din.readInt();
            String nodeIP = din.readUTF();
            int nodePort = din.readInt();
            System.out.println("Received registration request from " + nodeIP + ":" + nodePort);

            int responseType = 2; // REGISTER_RESPONSE PLACEHOLDER
            byte statusCode = 1; // SUCCESS
            String info = "Registration request successful.  The number of messaging nodes currently constituting the overlay is (1).";

            dout.writeInt(responseType);
            dout.writeByte(statusCode);
            dout.writeUTF(info);
            dout.flush();
            System.out.println("Sent registration response");

            din.close();
            dout.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}