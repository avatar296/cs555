package csx55.overlay.node;

import csx55.overlay.transport.TCPSender;
import csx55.overlay.wireformats.*;

import java.io.*;
import java.net.*;

public class MessagingNode {
    private String selfIp;
    private int selfPort;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java csx55.overlay.node.MessagingNode <registry-host> <registry-port>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        new MessagingNode().run(host, port);
    }

    private void run(String registryHost, int registryPort) throws Exception {
        // Auto-pick a listen port (not yet accepting peers—just reserving the
        // advertised port)
        ServerSocket ss = new ServerSocket(0);
        selfPort = ss.getLocalPort();
        selfIp = InetAddress.getLocalHost().getHostAddress();

        try (TCPSender toRegistry = new TCPSender(registryHost, registryPort)) {
            // REGISTER
            toRegistry.send(new Register(selfIp, selfPort));
            Event resp = toRegistry.read();
            if (!(resp instanceof RegisterResponse))
                throw new IOException("Unexpected reply");
            RegisterResponse rr = (RegisterResponse) resp;
            if (rr.status() != RegisterResponse.SUCCESS) {
                System.err.println("Registration failed: " + rr.info());
                return;
            }

            // CLI loop (only exit-overlay for now)
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.equals("exit-overlay")) {
                        toRegistry.send(new Deregister(selfIp, selfPort));
                        Event dr = toRegistry.read();
                        if (dr instanceof DeregisterResponse) {
                            System.out.println("exited overlay");
                        }
                        break;
                    }
                }
            }
        } finally {
            ss.close();
        }
    }
}