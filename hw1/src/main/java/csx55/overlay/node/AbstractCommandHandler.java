package csx55.overlay.node;

import java.util.Scanner;

/**
 * Abstract base class for command-line interface handlers.
 * Provides common functionality for interactive command loops used by
 * both Registry and MessagingNode command handlers.
 * 
 * Subclasses must implement the processCommand method to handle
 * specific commands for their node type.
 */
public abstract class AbstractCommandHandler {
    protected volatile boolean running = true;
    protected Scanner scanner;

    /**
     * Starts the interactive command loop.
     * Reads user input and delegates command processing to subclasses.
     * Handles resource cleanup when the loop terminates.
     */
    public void startCommandLoop() {
        scanner = new Scanner(System.in);
        try {
            while (running) {
                String input = scanner.nextLine();
                if (!running) {
                    break;
                }
                processCommand(input);
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /**
     * Processes a command input from the user.
     * Subclasses must implement this method to handle their specific commands.
     * 
     * @param input the raw command input from the user
     */
    protected abstract void processCommand(String input);

    /**
     * Stops the command loop.
     * Sets the running flag to false to terminate the loop gracefully.
     */
    public void stop() {
        running = false;
    }

    /**
     * Parses a command input into command and arguments.
     * 
     * @param input the raw input string
     * @return an array where index 0 is the command and remaining indices are
     *         arguments
     */
    protected String[] parseCommand(String input) {
        return input.trim().split("\\s+");
    }

    /**
     * Prints an error message for invalid number of arguments.
     * 
     * @param usage the correct usage format for the command
     */
    protected void printUsageError(String usage) {
        System.out.println("Usage: " + usage);
    }

    /**
     * Prints an error message for unknown commands.
     * 
     * @param command the unknown command
     */
    protected void printUnknownCommand(String command) {
        System.out.println("Unknown command: " + command);
    }

    /**
     * Safely parses an integer argument.
     * 
     * @param arg          the string to parse
     * @param errorMessage the error message to print if parsing fails
     * @return the parsed integer, or -1 if parsing failed
     */
    protected int parseIntArgument(String arg, String errorMessage) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            System.out.println(errorMessage);
            return -1;
        }
    }
}