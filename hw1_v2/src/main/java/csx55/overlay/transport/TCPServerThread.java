package csx55.overlay.transport;

import csx55.overlay.wireformats.Event;
import csx55.overlay.wireformats.EventFactory;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class TCPServerThread implements Runnable, AutoCloseable {
    public interface Handler {
        void handle(Socket s, DataInputStream in, DataOutputStream out) throws IOException;
    }

    private final ServerSocket server;
    private final Handler handler;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    public TCPServerThread(int port, Handler handler) throws IOException {
        this.server = new ServerSocket(port);
        this.handler = handler;
    }

    public int getLocalPort() {
        return server.getLocalPort();
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket s = server.accept();
                pool.submit(() -> {
                    try (DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                            DataOutputStream out = new DataOutputStream(
                                    new BufferedOutputStream(s.getOutputStream()))) {
                        handler.handle(s, in, out);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        server.close();
        pool.shutdownNow();
    }
}