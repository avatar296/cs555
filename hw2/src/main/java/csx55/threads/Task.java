package csx55.threads;

import java.io.Serializable;
import java.security.MessageDigest;
import java.util.Random;

public class Task implements Serializable {
  private final String id;
  private boolean poison;
  private boolean migrated;
  private int nonce;
  private String hash;

  private static final Random rand = new Random();

  public Task(String id) {
    this.id = id;
  }

  public static Task poisonPill() {
    Task t = new Task("POISON");
    t.poison = true;
    return t;
  }

  public boolean isPoison() {
    return poison;
  }

  public boolean isMigrated() {
    return migrated;
  }

  public void markMigrated() {
    this.migrated = true;
  }

  public void mine() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      while (true) {
        nonce = rand.nextInt(Integer.MAX_VALUE);
        byte[] encoded = digest.digest((id + nonce).getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : encoded) sb.append(String.format("%02x", b));
        hash = sb.toString();
        if (hash.startsWith("000")) break; // difficulty
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String toString() {
    return "Task{id="
        + id
        + ", nonce="
        + nonce
        + ", hash="
        + hash
        + ", migrated="
        + migrated
        + ", poison="
        + poison
        + "}";
  }
}
