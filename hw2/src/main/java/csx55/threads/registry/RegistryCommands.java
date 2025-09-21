package csx55.threads.registry;

public final class RegistryCommands {
  private RegistryCommands() {}

  public interface Actions {
    void setupOverlay(int size);

    void startRounds(int rounds);

    void shutdown();
  }

  public static boolean process(String line, Actions actions) {
    System.out.println("[DEBUG] RegistryCommands.process raw input: '" + line + "'");
    if (line == null) {
      System.out.println("[DEBUG] Input is null, returning true");
      return true;
    }
    String trimmed = line.trim();
    if (trimmed.isEmpty()) {
      System.out.println("[DEBUG] Input is empty after trim, returning true");
      return true;
    }

    String[] parts = trimmed.split("\\s+");
    String cmd = parts[0].toLowerCase();
    System.out.println(
        "[DEBUG] Parsed command: '" + cmd + "' with " + (parts.length - 1) + " arguments");

    try {
      switch (cmd) {
        case "setup-overlay":
          System.out.println("[DEBUG] Processing setup-overlay command");
          if (parts.length != 2) {
            System.out.println("Usage: setup-overlay <thread-pool-size>");
            return true;
          }
          int size = Integer.parseInt(parts[1]);
          System.out.println("[DEBUG] Parsed pool size: " + size);
          if (size < 2 || size > 16) {
            System.out.println("Thread pool size must be between 2 and 16");
            return true;
          }
          System.out.println("[DEBUG] Calling setupOverlay with size: " + size);
          actions.setupOverlay(size);
          System.out.println("[DEBUG] setupOverlay returned successfully");
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
