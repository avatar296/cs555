package csx55.overlay.node;

import java.util.Scanner;

/**
 * Handles command-line interface for MessagingNode.
 * Provides an interactive command loop for user interaction with the messaging node.
 * Supports commands for displaying the minimum spanning tree and exiting the overlay.
 */
public class MessagingNodeCommandHandler {
    private final MessagingNode node;
    private volatile boolean running = true;
    
    /**
     * Constructs a new MessagingNodeCommandHandler.
     * 
     * @param node the messaging node to control
     */
    public MessagingNodeCommandHandler(MessagingNode node) {
        this.node = node;
    }
    
    /**
     * Starts the interactive command loop.
     * Reads user input and executes corresponding commands.
     * Available commands:
     * - print-mst: displays the minimum spanning tree
     * - exit-overlay: deregisters from the overlay and exits
     */
    public void startCommandLoop() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            String command = scanner.nextLine();
            if ("print-mst".equalsIgnoreCase(command)) {
                node.printMinimumSpanningTree();
            } else if ("exit-overlay".equalsIgnoreCase(command)) {
                node.deregister();
                System.out.println("exited overlay");
                break;
            } else {
                System.out.println("Unknown command: " + command);
                System.out.println("Available commands: print-mst, exit-overlay");
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