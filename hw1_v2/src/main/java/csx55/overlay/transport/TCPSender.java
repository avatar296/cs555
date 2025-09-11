package csx55.overlay.transport;

import csx55.overlay.wireformats.Event;
import csx55.overlay.wireformats.EventFactory;
import java.io.*;
import java.net.*;

public class TCPSender implements Closeable {
  private final Socket socket;
  private final DataOutputStream out;
  private final DataInputStream in;

  public TCPSender(String host, int port) throws IOException {
    this.socket = new Socket();
    socket.connect(new InetSocketAddress(host, port), 5000);
    socket.setSoTimeout(0); // blocking
    this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
  }

  public synchronized void send(Event e) throws IOException {
    e.write(out);
    out.flush();
  }

  public Event read() throws IOException {
    return EventFactory.getInstance().read(in);
  }

  public Socket socket() {
    return socket;
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
