package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Singleton factory for creating Event objects from marshalled byte arrays.
 * Deserializes incoming
 * network data into appropriate Event implementations based on the protocol
 * message type.
 *
 * This factory maintains a single instance to ensure consistent event creation
 * throughout the
 * application.
 */
public class EventFactory {

  /** Singleton instance of the EventFactory */
  private static EventFactory instance = null;

  /** Private constructor to enforce singleton pattern. */
  private EventFactory() {
  }

  /**
   * Gets the singleton instance of the EventFactory. Creates the instance on
   * first access (lazy
   * initialization).
   *
   * @return the EventFactory instance
   */
  public static EventFactory getInstance() {
    if (instance == null) {
      instance = new EventFactory();
    }
    return instance;
  }

  /**
   * Creates an Event object from marshalled bytes. Reads the message type from
   * the byte array and
   * instantiates the appropriate Event implementation.
   *
   * @param marshalledBytes the serialized event data
   * @return the deserialized Event object
   * @throws IOException if deserialization fails or message type is unknown
   */
  public Event createEvent(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));

    int messageType = din.readInt();

    switch (messageType) {
      case Protocol.REGISTER_REQUEST:
        return new RegisterRequest(marshalledBytes);

      case Protocol.REGISTER_RESPONSE:
        return new RegisterResponse(marshalledBytes);

      case Protocol.DEREGISTER_REQUEST:
        return new DeregisterRequest(marshalledBytes);

      case Protocol.DEREGISTER_RESPONSE:
        return new DeregisterResponse(marshalledBytes);

      case Protocol.MESSAGING_NODES_LIST:
        return new MessagingNodesList(marshalledBytes);

      case Protocol.LINK_WEIGHTS:
        return new LinkWeights(marshalledBytes);

      case Protocol.TASK_INITIATE:
        return new TaskInitiate(marshalledBytes);

      case Protocol.TASK_COMPLETE:
        return new TaskComplete(marshalledBytes);

      case Protocol.PULL_TRAFFIC_SUMMARY:
        return new PullTrafficSummary(marshalledBytes);

      case Protocol.TRAFFIC_SUMMARY:
        return new TrafficSummary(marshalledBytes);

      case Protocol.DATA_MESSAGE:
        return new DataMessage(marshalledBytes);

      case Protocol.PEER_IDENTIFICATION:
        return new PeerIdentification(marshalledBytes);

      default:
        throw new IOException("Unknown message type: " + messageType);
    }
  }
}
