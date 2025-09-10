package csx55.threads;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Task: proof-of-work mining with SHA-256. */
public class Task implements java.io.Serializable {
  private static final long serialVersionUID = 1L;

  // Tunables via -D flags:
  // -Dcs555.difficultyBits=17 (spec default)
  // -Dcs555.printTasks=false (speed up smoke tests)
  private static final int DIFFICULTY_BITS = Integer.getInteger("cs555.difficultyBits", 17);
  private static final boolean PRINT_TASKS =
      Boolean.parseBoolean(System.getProperty("cs555.printTasks", "true"));

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
      byte[] originBytes = origin.getBytes();
      // Buffer = origin bytes + 4 bytes for nonce (big-endian)
      byte[] buf = Arrays.copyOf(originBytes, originBytes.length + 4);

      int nonce = 0;
      while (true) {
        // write nonce as big-endian into the last 4 bytes
        buf[buf.length - 4] = (byte) ((nonce >>> 24) & 0xFF);
        buf[buf.length - 3] = (byte) ((nonce >>> 16) & 0xFF);
        buf[buf.length - 2] = (byte) ((nonce >>> 8) & 0xFF);
        buf[buf.length - 1] = (byte) (nonce & 0xFF);

        byte[] hash = sha256.digest(buf);
        if (hasLeadingZeroBits(hash, DIFFICULTY_BITS)) {
          stats.incrementCompleted();
          if (PRINT_TASKS) {
            System.out.println(this);
          }
          break;
        }

        nonce++; // simple, fast stride; JIT-friendly
      }
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }

  // Fast bit-level check: true if hash has at least 'bits' leading zero bits.
  private static boolean hasLeadingZeroBits(byte[] hash, int bits) {
    int fullBytes = bits / 8;
    int remBits = bits % 8;

    for (int i = 0; i < fullBytes; i++) {
      if (hash[i] != 0) return false;
    }
    if (remBits == 0) return true;

    int mask = 0xFF << (8 - remBits); // e.g., remBits=4 -> 11110000
    return (hash[fullBytes] & mask) == 0;
  }

  public void markMigrated() {
    this.migrated = true;
  }

  @Override
  public String toString() {
    return "Task{" + origin + ", migrated=" + migrated + "}";
  }
}
