package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.IOException;

public final class EventFactory {
  private static final EventFactory INSTANCE = new EventFactory();

  private EventFactory() {}

  public static EventFactory getInstance() {
    return INSTANCE;
  }

  public Event read(DataInputStream in) throws IOException {
    int t = in.readInt();
    switch (t) {
      case Protocol.REGISTER:
        return new Register(in);
      case Protocol.REGISTER_RESPONSE:
        return new RegisterResponse(in);
      case Protocol.DEREGISTER:
        return new Deregister(in);
      case Protocol.DEREGISTER_RESPONSE:
        return new DeregisterResponse(in);
      case Protocol.MESSAGING_NODE_LIST:
        return new MessagingNodeList(in);
      case Protocol.LINK_WEIGHTS:
        return new LinkWeights(in);
      case Protocol.TASK_INITIATE:
        return new TaskInitiate(in);
      case Protocol.TASK_COMPLETE:
        return new TaskComplete(in);
      case Protocol.PULL_TRAFFIC_SUMMARY:
        return new PullTrafficSummary(in);
      case Protocol.TRAFFIC_SUMMARY:
        return new TrafficSummary(in);
      case Protocol.MESSAGE:
        return new Message(in);
      case Protocol.PEER_HELLO:
        return new PeerHello(in);

      default:
        throw new IOException("Unknown event type: " + t);
    }
  }
}
