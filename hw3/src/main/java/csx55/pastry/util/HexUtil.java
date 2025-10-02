package csx55.pastry.util;

/**
 * Utility class for converting between hexadecimal strings and byte arrays.
 *
 * <p>Code provided in Assignment Appendix A.
 */
public class HexUtil {

  /**
   * This method converts a set of bytes into a Hexadecimal representation.
   *
   * @param buf byte array to convert
   * @return hexadecimal string representation
   */
  public static String convertBytesToHex(byte[] buf) {
    StringBuffer strBuf = new StringBuffer();
    for (int i = 0; i < buf.length; i++) {
      int byteValue = (int) buf[i] & 0xff;
      if (byteValue <= 15) {
        strBuf.append("0");
      }
      strBuf.append(Integer.toString(byteValue, 16));
    }
    return strBuf.toString();
  }

  /**
   * This method converts a specified hexadecimal String into a set of bytes.
   *
   * @param hexString hexadecimal string to convert
   * @return byte array
   */
  public static byte[] convertHexToBytes(String hexString) {
    int size = hexString.length();
    byte[] buf = new byte[size / 2];
    int j = 0;
    for (int i = 0; i < size; i++) {
      String a = hexString.substring(i, i + 2);
      int valA = Integer.parseInt(a, 16);
      i++;
      buf[j] = (byte) valA;
      j++;
    }
    return buf;
  }

  /**
   * Validates that a string is a valid 16-bit (4 character) hexadecimal ID.
   *
   * @param hexId the hex ID to validate
   * @return true if valid, false otherwise
   */
  public static boolean isValidHexId(String hexId) {
    if (hexId == null || hexId.length() != 4) {
      return false;
    }
    return hexId.matches("[0-9a-fA-F]{4}");
  }

  /**
   * Normalizes a hex ID to lowercase.
   *
   * @param hexId the hex ID to normalize
   * @return normalized lowercase hex ID
   */
  public static String normalize(String hexId) {
    return hexId.toLowerCase();
  }
}
