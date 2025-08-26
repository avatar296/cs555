package csx55.overlay.node;

import java.util.Scanner;

/**
 * Handles command-line interface for MessagingNode
 * This class simplifies MessagingNode.java by extracting command handling logic
 */
public class MessagingNodeCommandHandler {
    private final MessagingNode node;
    private volatile boolean running = true;
    
    public MessagingNodeCommandHandler(MessagingNode node) {
        this.node = node;
    }
    
    public void startCommandLoop() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            String command = scanner.nextLine();
            if ("print-mst".equalsIgnoreCase(command)) {
                node.printMinimumSpanningTree();
            } else if ("exit-overlay".equalsIgnoreCase(command)) {
                node.deregister();
                break;
            } else {
                System.out.println("Unknown command: " + command);
                System.out.println("Available commands: print-mst, exit-overlay");
            }
        }
        scanner.close();
    }
    
    public void stop() {
        running = false;
    }
}