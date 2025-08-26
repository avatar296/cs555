package csx55.overlay.node;

import java.util.Scanner;

/**
 * Handles command-line interface for Registry
 * This class simplifies Registry.java by extracting command handling logic
 */
public class RegistryCommandHandler {
    private final Registry registry;
    private volatile boolean running = true;
    
    public RegistryCommandHandler(Registry registry) {
        this.registry = registry;
    }
    
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
    
    public void stop() {
        running = false;
    }
}