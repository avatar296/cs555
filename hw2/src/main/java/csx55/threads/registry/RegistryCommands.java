package csx55.threads.registry;

public final class RegistryCommands {
  private RegistryCommands() {}

  public interface Actions {
    void setupOverlay(int size);

    void startRounds(int rounds);

    void shutdown();
  }

  public static boolean process(String line, Actions actions) {
    if (line == null) {
      return true;
    }
    String trimmed = line.trim();
    if (trimmed.isEmpty()) {
      return true;
    }

    String[] parts = trimmed.split("\\s+");
    String cmd = parts[0].toLowerCase();

    try {
      switch (cmd) {
        case "setup-overlay":
          if (parts.length != 2) {
            System.out.println("Usage: setup-overlay <thread-pool-size>");
            return true;
          }
          int size = Integer.parseInt(parts[1]);
          if (size < 2 || size > 16) {
            System.out.println("Thread pool size must be between 2 and 16");
            return true;
          }
          actions.setupOverlay(size);
          return true;

        case "start":
          if (parts.length != 2) {
            System.out.println("Usage: start <number-of-rounds>");
            return true;
          }
          int rounds = Integer.parseInt(parts[1]);
          if (rounds <= 0) {
            System.out.println("number-of-rounds must be > 0");
            return true;
          }
          actions.startRounds(rounds);
          return true;

        case "quit":
          actions.shutdown();
          return false;

        default:
          System.out.println("Unknown command: " + line);
          System.out.println("Available: setup-overlay <size>, start <rounds>, quit");
          return true;
      }
    } catch (NumberFormatException nfe) {
      System.out.println("Invalid number format: " + nfe.getMessage());
      return true;
    }
  }
}
