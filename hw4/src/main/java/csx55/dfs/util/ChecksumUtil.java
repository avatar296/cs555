/* CS555 Distributed Systems - HW4 */
package csx55.dfs.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Utility class for computing and verifying SHA-1 checksums Used for ensuring data integrity in
 * chunks
 */
public class ChecksumUtil {

    private static final int SLICE_SIZE = 8 * 1024; // 8KB

    /**
     * Compute SHA-1 checksum for a single slice of data
     *
     * @param data The data to checksum
     * @param offset Starting offset in the data
     * @param length Length of data to checksum
     * @return 20-byte SHA-1 digest
     */
    public static byte[] computeChecksum(byte[] data, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(data, offset, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    /** Compute SHA-1 checksum for entire data */
    public static byte[] computeChecksum(byte[] data) {
        return computeChecksum(data, 0, data.length);
    }

    /**
     * Compute checksums for all 8KB slices in a chunk
     *
     * @param chunkData The chunk data (up to 64KB)
     * @return Array of checksums, one for each 8KB slice
     */
    public static byte[][] computeSliceChecksums(byte[] chunkData) {
        int numSlices = (int) Math.ceil((double) chunkData.length / SLICE_SIZE);
        byte[][] checksums = new byte[numSlices][];

        for (int i = 0; i < numSlices; i++) {
            int offset = i * SLICE_SIZE;
            int length = Math.min(SLICE_SIZE, chunkData.length - offset);
            checksums[i] = computeChecksum(chunkData, offset, length);
        }

        return checksums;
    }

    /**
     * Verify checksums for all slices in a chunk
     *
     * @param chunkData The chunk data
     * @param storedChecksums The stored checksums to verify against
     * @return -1 if all valid, otherwise the index of the first corrupted slice (0-based)
     */
    public static int verifySliceChecksums(byte[] chunkData, byte[][] storedChecksums) {
        byte[][] computedChecksums = computeSliceChecksums(chunkData);

        if (computedChecksums.length != storedChecksums.length) {
            return 0; // Size mismatch indicates corruption
        }

        for (int i = 0; i < computedChecksums.length; i++) {
            if (!Arrays.equals(computedChecksums[i], storedChecksums[i])) {
                return i; // Return 0-based index of corrupted slice
            }
        }

        return -1; // All slices valid
    }

    /** Convert checksum bytes to hex string (for debugging) */
    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
