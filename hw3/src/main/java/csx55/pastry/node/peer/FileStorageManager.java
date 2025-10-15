package csx55.pastry.node.peer;

import csx55.pastry.util.HashUtil;
import csx55.pastry.util.HexUtil;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class FileStorageManager {
  private static final Logger logger = Logger.getLogger(FileStorageManager.class.getName());

  private final File storageDir;
  private final PeerStatistics statistics;

  public FileStorageManager(String peerId, PeerStatistics statistics) {
    this.storageDir = new File("/tmp/" + peerId);
    this.statistics = statistics;
    // Defer directory creation until first use to speed up startup
  }

  private void ensureStorageDirectory() {
    if (!storageDir.exists()) {
      if (storageDir.mkdirs()) {
        logger.info("Created storage directory: " + storageDir.getAbsolutePath());
      } else {
        logger.warning("Failed to create storage directory");
      }
    }
  }

  public File getStorageDir() {
    return storageDir;
  }

  public void storeFile(String filename, byte[] fileData) throws IOException {
    ensureStorageDirectory();
    File file = new File(storageDir, filename);
    java.nio.file.Files.write(file.toPath(), fileData);
    statistics.incrementFilesStored();
    logger.info("Stored file: " + filename + " (" + fileData.length + " bytes)");
  }

  public byte[] retrieveFile(String filename) throws IOException {
    File file = new File(storageDir, filename);

    if (!file.exists()) {
      logger.warning("File not found: " + filename);
      return null;
    }

    byte[] fileData = java.nio.file.Files.readAllBytes(file.toPath());
    logger.info("Retrieved file: " + filename + " (" + fileData.length + " bytes)");
    return fileData;
  }

  public String[] listFilesWithHashes() {
    ensureStorageDirectory();
    File[] files = storageDir.listFiles();
    if (files == null || files.length == 0) {
      return new String[0];
    }

    java.util.List<String> result = new java.util.ArrayList<>();
    for (File file : files) {
      if (file.isFile()) {
        String filename = file.getName();
        String hash = HexUtil.convertBytesToHex(HashUtil.hash16(filename));
        result.add(filename + ", " + hash);
      }
    }

    return result.toArray(new String[0]);
  }
}
