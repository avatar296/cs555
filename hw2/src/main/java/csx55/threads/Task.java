package csx55.threads;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class Task {
  private final String id;
  private boolean poison; // no longer final
  private int nonce;
  private String hash;
  private boolean migrated;

  private static final Random random = new Random();

  public Task(String id) {
    this.id = id;
    this.poison = false;
    this.nonce = 0;
    this.hash = null;
    this.migrated = false;
  }

  public static Task poisonPill() {
    Task t = new Task("POISON");
    t.poison = true;
    return t;
  }

  public boolean isPoison() {
    return poison;
  }

  public String getId() {
    return id;
  }

  public boolean isMigrated() {
    return migrated;
  }

  public void setMigrated(boolean migrated) {
    this.migrated = migrated;
  }

  // For TaskQueue compatibility
  public void markMigrated() {
    this.migrated = true;
  }

  public void mine() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      while (true) {
        nonce = random.nextInt(Integer.MAX_VALUE);
        String input = id + nonce;
        byte[] encoded = digest.digest(input.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : encoded) {
          sb.append(String.format("%02x", b));
        }
        hash = sb.toString();

        // Easier difficulty: only 3 leading zeros
        if (hash.startsWith("000")) {
          break;
        }
      }
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String toString() {
    return "Task{id='"
        + id
        + "', nonce="
        + nonce
        + ", hash="
        + hash
        + ", migrated="
        + migrated
        + "}";
  }
}
