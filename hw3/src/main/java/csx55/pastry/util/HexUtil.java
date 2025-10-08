package csx55.pastry.util;

public class HexUtil {

  public static String convertBytesToHex(byte[] buf) {
    StringBuilder strBuf = new StringBuilder();
    for (int i = 0; i < buf.length; i++) {
      int byteValue = (int) buf[i] & 0xff;
      if (byteValue <= 15) {
        strBuf.append("0");
      }
      strBuf.append(Integer.toString(byteValue, 16));
    }
    return strBuf.toString();
  }

  public static boolean isValidHexId(String hexId) {
    if (hexId == null || hexId.length() != 4) {
      return false;
    }
    return hexId.matches("[0-9a-fA-F]{4}");
  }

  public static String normalize(String hexId) {
    return hexId.toLowerCase();
  }
}
