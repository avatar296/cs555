package csx55.overlay.util;

import csx55.overlay.wireformats.TrafficSummary;

import java.util.*;

public class StatisticsCollector {
    
    private Map<String, TrafficSummary> summaries;
    private int expectedNodes;
    
    public StatisticsCollector(int expectedNodes) {
        this.expectedNodes = expectedNodes;
        this.summaries = new HashMap<>();
    }
    
    public synchronized void addSummary(TrafficSummary summary) {
        summaries.put(summary.getNodeId(), summary);
    }
    
    public synchronized boolean hasAllSummaries() {
        return summaries.size() == expectedNodes;
    }
    
    public synchronized void reset() {
        summaries.clear();
    }
    
    public synchronized void printStatistics() {
        if (summaries.isEmpty()) {
            System.out.println("No statistics available.");
            return;
        }
        
        // Sort nodes by ID for consistent output
        List<String> sortedNodeIds = new ArrayList<>(summaries.keySet());
        Collections.sort(sortedNodeIds);
        
        long totalSent = 0;
        long totalReceived = 0;
        long totalRelayed = 0;
        long totalSumSent = 0;
        long totalSumReceived = 0;
        
        // Print individual node statistics
        for (String nodeId : sortedNodeIds) {
            TrafficSummary summary = summaries.get(nodeId);
            
            System.out.printf("%s %d %d %.2f %.2f %d%n",
                nodeId,
                summary.getMessagesSent(),
                summary.getMessagesReceived(),
                (double)summary.getSumSentMessages(),
                (double)summary.getSumReceivedMessages(),
                summary.getMessagesRelayed()
            );
            
            totalSent += summary.getMessagesSent();
            totalReceived += summary.getMessagesReceived();
            totalRelayed += summary.getMessagesRelayed();
            totalSumSent += summary.getSumSentMessages();
            totalSumReceived += summary.getSumReceivedMessages();
        }
        
        // Print totals
        System.out.printf("sum %d %d %.2f %.2f%n",
            totalSent,
            totalReceived,
            (double)totalSumSent,
            (double)totalSumReceived
        );
        
        // Verify correctness
        if (totalSent != totalReceived) {
            System.out.println("WARNING: Total sent (" + totalSent + ") != Total received (" + totalReceived + ")");
        }
        
        if (totalSumSent != totalSumReceived) {
            System.out.println("WARNING: Sum of sent (" + totalSumSent + ") != Sum of received (" + totalSumReceived + ")");
        }
    }
    
    public synchronized Map<String, TrafficSummary> getSummaries() {
        return new HashMap<>(summaries);
    }
}