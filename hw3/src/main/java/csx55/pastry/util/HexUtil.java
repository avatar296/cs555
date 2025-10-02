package csx55.pastry.util;

// Code from Assignment Appendix A
public class HexUtil {

  // Convert bytes to hexadecimal string
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

  // Convert hexadecimal string to bytes
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

  // Check if string is valid 4-digit hex ID
  public static boolean isValidHexId(String hexId) {
    if (hexId == null || hexId.length() != 4) {
      return false;
    }
    return hexId.matches("[0-9a-fA-F]{4}");
  }

  // Normalize to lowercase
  public static String normalize(String hexId) {
    return hexId.toLowerCase();
  }
}
