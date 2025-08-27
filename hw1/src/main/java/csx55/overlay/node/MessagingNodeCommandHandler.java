package csx55.overlay.node;

/**
 * Handles command-line interface for MessagingNode.
 * Provides an interactive command loop for user interaction with the messaging
 * node.
 * Supports commands for displaying the minimum spanning tree and exiting the
 * overlay.
 */
public class MessagingNodeCommandHandler extends AbstractCommandHandler {
    private final MessagingNode node;

    /**
     * Constructs a new MessagingNodeCommandHandler.
     * 
     * @param node the messaging node to control
     */
    public MessagingNodeCommandHandler(MessagingNode node) {
        this.node = node;
    }

    /**
     * Processes commands specific to MessagingNode.
     * Available commands:
     * - print-mst: displays the minimum spanning tree
     * - exit-overlay: deregisters from the overlay and exits
     * 
     * @param input the raw command input from the user
     */
    @Override
    protected void processCommand(String input) {
        String command = input.trim().toLowerCase();

        if ("print-mst".equals(command)) {
            node.printMinimumSpanningTree();
        } else if ("exit-overlay".equals(command)) {
            node.deregister();
            System.out.println("exited overlay");
            stop();
        } else {
            printUnknownCommand(command);
            System.out.println("Available commands: print-mst, exit-overlay");
        }
    }
}