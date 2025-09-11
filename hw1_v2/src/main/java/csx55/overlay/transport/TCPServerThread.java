// src/main/java/csx55/overlay/transport/TCPServerThread.java
package csx55.overlay.transport;

import csx55.overlay.wireformats.EventFactory;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal threaded TCP acceptor that delegates each client socket to a handler.
 * No println() to stdout (keeps autograder output clean).
 */
public final class TCPServerThread implements Runnable {
    @FunctionalInterface
    public interface ClientHandler {
        void handle(Socket s, DataInputStream in, DataOutputStream out) throws IOException;
    }

    private final int port;
    private final ClientHandler handler;
    private volatile ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public TCPServerThread(int port, ClientHandler handler) throws IOException {
        this.port = port;
        this.handler = handler;
        this.server = new ServerSocket(port);
    }

    @Override
    public void run() {
        try {
            while (!server.isClosed()) {
                final Socket s = server.accept();
                pool.submit(() -> {
                    try (Socket sock = s;
                            DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
                            DataOutputStream out = new DataOutputStream(
                                    new BufferedOutputStream(sock.getOutputStream()))) {
                        handler.handle(sock, in, out);
                    } catch (IOException ignored) {
                        // client closed or handler error; ignore
                    }
                });
            }
        } catch (IOException ignored) {
        } finally {
            try {
                server.close();
            } catch (IOException ignored) {
            }
            pool.shutdownNow();
        }
    }

    public int getPort() {
        return port;
    }

    public void close() {
        try {
            server.close();
        } catch (IOException ignored) {
        }
    }
}
