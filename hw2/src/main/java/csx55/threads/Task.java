package csx55.threads;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/** Task: proof-of-work mining with SHA-256. */
public class Task implements java.io.Serializable {

  private static final long serialVersionUID = 1L;
  private final String origin;
  private final Stats stats;
  private boolean migrated = false;

  public Task(String origin, Stats stats) {
    this.origin = origin;
    this.stats = stats;
  }

  public void mine() {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      Random rand = new Random();
      int nonce = 0;
      while (true) {
        byte[] input = (origin + nonce).getBytes();
        byte[] hash = sha256.digest(input);
        String hex = bytesToHex(hash);
        // Require at least 17 leading zeros in binary (≈5 hex zeros)
        if (hex.startsWith("00000")) {
          stats.incrementCompleted();
          System.out.println(this);
          break;
        }
        nonce += rand.nextInt(100) + 1;
      }
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  public void markMigrated() {
    this.migrated = true;
  }

  @Override
  public String toString() {
    return "Task{" + origin + ", migrated=" + migrated + "}";
  }
}
