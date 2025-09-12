package csx55.threads.util;

public final class Config {
  private Config() {}

  public static int getInt(String key, int def) {
    try {
      String v = System.getProperty(key);
      return v == null ? def : Integer.parseInt(v.trim());
    } catch (Exception e) {
      return def;
    }
  }

  public static long getLong(String key, long def) {
    try {
      String v = System.getProperty(key);
      return v == null ? def : Long.parseLong(v.trim());
    } catch (Exception e) {
      return def;
    }
  }

  public static boolean getBool(String key, boolean def) {
    try {
      String v = System.getProperty(key);
      return v == null ? def : Boolean.parseBoolean(v.trim());
    } catch (Exception e) {
      return def;
    }
  }
}
