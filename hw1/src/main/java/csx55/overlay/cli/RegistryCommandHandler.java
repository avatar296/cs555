package csx55.overlay.cli;

import csx55.overlay.node.Registry;

/**
 * Handles command-line interface for the Registry. Provides an interactive command loop for
 * managing the overlay network.
 *
 * <p>Available commands: - list-messaging-nodes: displays all registered nodes - list-weights:
 * shows all link weights in the overlay - setup-overlay <number>: configures the overlay with
 * specified connections - send-overlay-link-weights: distributes link weights to all nodes - start
 * <number>: initiates a messaging task with specified rounds
 */
public class RegistryCommandHandler extends AbstractCommandHandler {
  private final Registry registry;

  /**
   * Constructs a new RegistryCommandHandler.
   *
   * @param registry the registry to control
   */
  public RegistryCommandHandler(Registry registry) {
    this.registry = registry;
  }

  /**
   * Processes commands specific to Registry. Handles command parsing and delegates to appropriate
   * registry methods.
   *
   * @param input the raw command input from the user
   */
  @Override
  protected void processCommand(String input) {
    String[] parts = parseCommand(input);
    if (parts.length == 0) {
      return;
    }

    String command = parts[0].toLowerCase(java.util.Locale.ROOT);

    switch (command) {
      case "list-messaging-nodes":
        registry.listMessagingNodes();
        break;

      case "list-weights":
        registry.listWeights();
        break;

      case "setup-overlay":
        if (parts.length == 2) {
          int connections =
              parseIntArgument(parts[1], "Invalid number argument for setup-overlay command.");
          if (connections == -1) {
            return;
          } else if (connections <= 0) {
            System.out.println("Number of connections must be greater than 0");
          } else {
            registry.setupOverlay(connections);
          }
        } else {
          printUsageError("setup-overlay <number-of-connections>");
        }
        break;

      case "send-overlay-link-weights":
        registry.sendOverlayLinkWeights();
        break;

      case "start":
        if (parts.length == 2) {
          int rounds = parseIntArgument(parts[1], "Invalid number argument for start command.");
          if (rounds == -1) {
            return;
          } else if (rounds <= 0) {
            System.out.println("Number of rounds must be greater than 0");
          } else {
            registry.startMessaging(rounds);
          }
        } else {
          printUsageError("start <number-of-rounds>");
        }
        break;

      default:
        System.out.println("Unknown command.");
    }
  }
}
