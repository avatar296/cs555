package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Abstract base class for all Event implementations. Provides common serialization/deserialization
 * framework using the Template Method pattern. Handles stream creation, resource management, and
 * common operations.
 *
 * <p>Subclasses must implement: - writeData() to serialize their specific fields - readData() to
 * deserialize their specific fields - getType() to return their message type
 */
public abstract class AbstractEvent implements Event {

  /**
   * Template method for serializing the event to bytes. Creates streams, writes message type,
   * delegates to subclass for data, and ensures proper resource cleanup.
   *
   * @return the serialized message as a byte array
   * @throws IOException if serialization fails
   */
  @Override
  public final byte[] getBytes() throws IOException {
    ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();
    DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(baOutputStream));

    try {
      // Write message type
      dout.writeInt(getType());

      // Delegate to subclass for specific data
      writeData(dout);

      // Ensure all data is written
      dout.flush();

      return baOutputStream.toByteArray();
    } finally {
      // Ensure streams are closed
      closeQuietly(dout);
      closeQuietly(baOutputStream);
    }
  }

  /**
   * Template method for deserializing from bytes. Creates streams, validates message type,
   * delegates to subclass for data, and ensures proper resource cleanup.
   *
   * @param marshalledBytes the serialized message data
   * @throws IOException if deserialization fails or message type is invalid
   */
  protected final void deserializeFrom(byte[] marshalledBytes) throws IOException {
    ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
    DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));

    try {
      // Validate message type
      int messageType = din.readInt();
      if (messageType != getType()) {
        throw new IOException(
            "Invalid message type. Expected " + getType() + " but received " + messageType);
      }

      // Delegate to subclass for specific data
      readData(din);
    } finally {
      // Ensure streams are closed
      closeQuietly(din);
      closeQuietly(baInputStream);
    }
  }

  /**
   * Write the event-specific data to the output stream. Subclasses must implement this to serialize
   * their fields.
   *
   * @param dout the data output stream
   * @throws IOException if writing fails
   */
  protected abstract void writeData(DataOutputStream dout) throws IOException;

  /**
   * Read the event-specific data from the input stream. Subclasses must implement this to
   * deserialize their fields.
   *
   * @param din the data input stream
   * @throws IOException if reading fails
   */
  protected abstract void readData(DataInputStream din) throws IOException;

  /**
   * Utility method to write a string array to the output stream. Common pattern used by multiple
   * event types.
   *
   * @param dout the output stream
   * @param array the string array to write
   * @throws IOException if writing fails
   */
  protected static void writeStringArray(DataOutputStream dout, String[] array) throws IOException {
    dout.writeInt(array.length);
    for (String s : array) {
      dout.writeUTF(s);
    }
  }

  /**
   * Utility method to read a string array from the input stream. Common pattern used by multiple
   * event types.
   *
   * @param din the input stream
   * @return the read string array
   * @throws IOException if reading fails
   */
  protected static String[] readStringArray(DataInputStream din) throws IOException {
    int length = din.readInt();
    String[] array = new String[length];
    for (int i = 0; i < length; i++) {
      array[i] = din.readUTF();
    }
    return array;
  }

  /**
   * Closes a resource quietly, suppressing any exceptions. Used for cleanup in finally blocks.
   *
   * @param closeable the resource to close
   */
  private static void closeQuietly(AutoCloseable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception ignored) {
        // Suppress exceptions during cleanup
      }
    }
  }
}
