package csx55.pastry.util;

public class HashUtil {
  /**
   * Computes a 16-bit hash of a filename for Pastry DHT routing. Per PDF Appendix A specification.
   *
   * @param fileName The filename to hash
   * @return 2-byte array containing the 16-bit hash
   */
  public static byte[] hash16(String fileName) {
    int h = fileName.hashCode() & 0xFFFF;
    byte[] result = new byte[2];
    result[0] = (byte) ((h >>> 8) & 0xFF);
    result[1] = (byte) (h & 0xFF);
    return result;
  }

  /**
   * Computes a 16-bit hash of a filename for Pastry DHT routing. Legacy method - returns hex
   * string.
   *
   * @param filename The filename to hash
   * @return A 4-character hex string (16 bits)
   */
  public static String hashFilename(String filename) {
    byte[] hashBytes = hash16(filename);
    return HexUtil.convertBytesToHex(hashBytes);
  }
}
