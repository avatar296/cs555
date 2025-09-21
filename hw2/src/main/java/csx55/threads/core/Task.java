package csx55.threads.core;

import csx55.threads.util.Config;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

public class Task implements java.io.Serializable {
  private static final long serialVersionUID = 1L;

  private static final int DIFFICULTY_BITS = Config.getInt("cs555.difficultyBits", 17);

  private final String id;
  private final String origin;
  private String minedAt;
  private final long timestamp;
  private boolean migrated = false;
  private int nonce = -1;
  private String hashHex = null;

  public Task(String origin) {
    this.id = UUID.randomUUID().toString().substring(0, 8);
    this.origin = origin;
    this.minedAt = origin; // Initially, assume mined where created
    this.timestamp = System.currentTimeMillis();
  }

  public void mine() {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] originBytes = origin.getBytes();
      byte[] buf = Arrays.copyOf(originBytes, originBytes.length + 4);

      int nonce = 0;
      for (; ; ) {
        buf[buf.length - 4] = (byte) ((nonce >>> 24) & 0xFF);
        buf[buf.length - 3] = (byte) ((nonce >>> 16) & 0xFF);
        buf[buf.length - 2] = (byte) ((nonce >>> 8) & 0xFF);
        buf[buf.length - 1] = (byte) (nonce & 0xFF);

        byte[] hash = sha256.digest(buf);
        if (hasLeadingZeroBits(hash, DIFFICULTY_BITS)) {
          this.nonce = nonce;
          this.hashHex = bytesToHex(hash);
          return;
        }
        nonce++;
      }
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean hasLeadingZeroBits(byte[] hash, int bits) {
    int full = bits / 8, rem = bits % 8;
    for (int i = 0; i < full; i++) if (hash[i] != 0) return false;
    if (rem == 0) return true;
    int mask = 0xFF << (8 - rem);
    return (hash[full] & mask) == 0;
  }

  public void setMinedAt(String nodeId) {
    this.minedAt = nodeId;
  }

  public void markMigrated() {
    this.migrated = true;
  }

  public boolean isMigrated() {
    return migrated;
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte b : bytes) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Task[");
    sb.append("id=").append(id);
    sb.append(", minedAt=").append(minedAt); // Show where it was actually mined
    sb.append(", origin=").append(origin);
    sb.append(", timestamp=").append(timestamp);
    if (nonce >= 0) {
      sb.append(", nonce=").append(nonce);
    }
    if (hashHex != null) {
      sb.append(", hash=")
          .append(hashHex.substring(0, Math.min(16, hashHex.length())))
          .append("...");
    }
    sb.append(", migrated=").append(migrated);
    sb.append("]");
    return sb.toString();
  }
}
