package csx55.overlay.wireformats;

import java.io.IOException;

/**
 * Base interface for all network events in the overlay system. Defines the
 * contract for messages
 * that can be serialized and transmitted over the network.
 *
 * All implementing classes must provide: - A unique message type identifier -
 * Serialization to
 * byte array for network transmission
 */
public interface Event {

  /**
   * Gets the protocol type identifier for this event. The type must match one of
   * the constants
   * defined in Protocol interface.
   *
   * @return the protocol message type
   */
  int getType();

  /**
   * Serializes this event into a byte array for network transmission. The
   * serialized format must be
   * compatible with the deserialization constructor of the implementing class.
   *
   * @return byte array representation of this event
   * @throws IOException if serialization fails
   */
  byte[] getBytes() throws IOException;
}
