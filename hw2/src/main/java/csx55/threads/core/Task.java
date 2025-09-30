package csx55.threads.core;

import csx55.threads.util.Config;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class Task implements java.io.Serializable {
  private static final long serialVersionUID = 1L;

  private static final int DIFFICULTY_BITS = Config.getInt("cs555.difficultyBits", 17);

  private final String ip;
  private final int port;
  private final int roundNumber;
  private final int payload;
  private long timestamp;
  private long threadId;
  private int nonce;
  private boolean migrated = false;

  public Task(String ip, int port, int roundNumber, int payload) {
    this.ip = ip;
    this.port = port;
    this.roundNumber = roundNumber;
    this.payload = payload;
    this.timestamp = 0L;
    this.threadId = 0L;
    this.nonce = 0;
    this.migrated = false;
  }

  public void mine() {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA3-256");
      this.threadId = Thread.currentThread().getId();
      Random random = new Random();

      while (true) {
        this.timestamp = System.currentTimeMillis();
        this.nonce = random.nextInt();
        byte[] hash = sha256.digest(toBytes());
        if (hasLeadingZeroBits(hash, DIFFICULTY_BITS)) {
          return;
        }
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

  public void markMigrated() {
    this.migrated = true;
  }

  public boolean isMigrated() {
    return migrated;
  }

  public byte[] toBytes() {
    return toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return ip
        + ":"
        + port
        + ":"
        + roundNumber
        + ":"
        + payload
        + ":"
        + timestamp
        + ":"
        + threadId
        + ":"
        + nonce;
  }
}
