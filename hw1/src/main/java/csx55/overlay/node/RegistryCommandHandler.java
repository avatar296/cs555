package csx55.overlay.node;

import java.util.Scanner;

/**
 * Handles command-line interface for the Registry.
 * Provides an interactive command loop for managing the overlay network.
 * 
 * Available commands:
 * - list-messaging-nodes: displays all registered nodes
 * - list-weights: shows all link weights in the overlay
 * - setup-overlay <number>: configures the overlay with specified connections
 * - send-overlay-link-weights: distributes link weights to all nodes
 * - start <number>: initiates a messaging task with specified rounds
 */
public class RegistryCommandHandler {
    private final Registry registry;
    private volatile boolean running = true;
    
    /**
     * Constructs a new RegistryCommandHandler.
     * 
     * @param registry the registry to control
     */
    public RegistryCommandHandler(Registry registry) {
        this.registry = registry;
    }
    
    /**
     * Starts the interactive command loop.
     * Reads user input, parses commands, and executes corresponding operations.
     * Handles command parsing errors and provides usage information.
     */
    public void startCommandLoop() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            String input = scanner.nextLine();
            String[] parts = input.trim().split("\\s+");
            String command = parts[0];
            
            try {
                switch (command.toLowerCase()) {
                    case "list-messaging-nodes":
                        registry.listMessagingNodes();
                        break;
                    case "list-weights":
                        registry.listWeights();
                        break;
                    case "setup-overlay":
                        if (parts.length == 2) {
                            registry.setupOverlay(Integer.parseInt(parts[1]));
                        } else {
                            System.out.println("Usage: setup-overlay <number-of-connections>");
                        }
                        break;
                    case "send-overlay-link-weights":
                        registry.sendOverlayLinkWeights();
                        break;
                    case "start":
                        if (parts.length == 2) {
                            registry.startMessaging(Integer.parseInt(parts[1]));
                        } else {
                            System.out.println("Usage: start <number-of-rounds>");
                        }
                        break;
                    default:
                        System.out.println("Unknown command.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid number argument for command.");
            }
        }
        scanner.close();
    }
    
    /**
     * Stops the command loop.
     * Sets the running flag to false to terminate the loop.
     */
    public void stop() {
        running = false;
    }
}