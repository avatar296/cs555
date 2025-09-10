package csx55.threads;

import java.security.*;

public class Task {
  private byte[] data;

  public Task(byte[] data) {
    this.data = data;
  }

  public byte[] getBytes() {
    return data;
  }

  public boolean mine() {
    // Perform SHA-256 proof-of-work
    return false; // placeholder
  }

  @Override
  public String toString() {
    // Print mined task info
    return "Task[data...]";
  }
}
