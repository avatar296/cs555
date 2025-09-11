package csx55.threads;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Task implements java.io.Serializable {
  private static final long serialVersionUID = 1L;

  private static final int DIFFICULTY_BITS = Integer.getInteger("cs555.difficultyBits", 17);

  private final String origin;
  private boolean migrated = false;

  public Task(String origin) {
    this.origin = origin;
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

  public void markMigrated() {
    this.migrated = true;
  }

  public boolean isMigrated() {
    return migrated;
  }

  @Override
  public String toString() {
    return "Task{" + origin + ", migrated=" + migrated + "}";
  }
}
