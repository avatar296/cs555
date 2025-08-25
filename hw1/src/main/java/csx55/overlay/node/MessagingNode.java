package csx55.overlay.node;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class MessagingNode {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java overlay.node.MessagingNode <registry-host> <registry-port>");
            return;
        }

        String registryHost = args[0];
        int registryPort = Integer.parseInt(args[1]);

        try (Socket socketToRegistry = new Socket(registryHost, registryPort)) {
            System.out.println("Connected to registry.");

            DataOutputStream dout = new DataOutputStream(socketToRegistry.getOutputStream());
            DataInputStream din = new DataInputStream(socketToRegistry.getInputStream());

            int messageType = 1; // REGISTRY_REQUEST Placeholder
            String nodeIP = socketToRegistry.getLocalAddress().getHostAddress();
            int nodePort = 50001;

            dout.writeInt(messageType);
            dout.writeUTF(nodeIP);
            dout.writeInt(nodePort);
            dout.flush();

            System.out.println("Sent registration request to the Registry");

            // Receive registration response
            int responseType = din.readInt();
            byte statusCode = din.readByte();
            String additionalInfo = din.readUTF();

            System.out.println("\nReceived response from Registry.");
            if (responseType == 2) {
                System.out.println(("Status: " + (statusCode == 1 ? "SUCCESS" : "FAILURE")));
                System.out.println("Info: " + additionalInfo);
            }
            din.close();
            dout.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}