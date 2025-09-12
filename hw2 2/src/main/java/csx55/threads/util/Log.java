package csx55.threads.util;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public final class Log {
  private Log() {}

  private static String ts() {
    return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
  }

  private static String thread() {
    return Thread.currentThread().getName();
  }

  public static void info(String msg) {
    System.out.println("[INFO] " + ts() + " [" + thread() + "] " + msg);
  }

  public static void warn(String msg) {
    System.out.println("[WARN] " + ts() + " [" + thread() + "] " + msg);
  }

  public static void error(String msg) {
    System.err.println("[ERROR] " + ts() + " [" + thread() + "] " + msg);
  }
}
