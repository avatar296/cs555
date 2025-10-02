package csx55.pastry.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {
  /**
   * Computes a 16-bit hash of a filename for Pastry DHT routing.
   *
   * @param filename The filename to hash
   * @return A 4-character hex string (16 bits)
   */
  public static String hashFilename(String filename) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(filename.getBytes(StandardCharsets.UTF_8));

      // Take first 2 bytes (16 bits) and convert to hex
      int hash16bit = ((hashBytes[0] & 0xFF) << 8) | (hashBytes[1] & 0xFF);
      String hexHash = String.format("%04x", hash16bit);

      return hexHash;
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }
}
