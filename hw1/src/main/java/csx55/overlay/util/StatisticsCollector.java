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
        long totalSumSent = 0;
        long totalSumReceived = 0;
        
        // Print individual node statistics
        for (String nodeId : sortedNodeIds) {
            TrafficSummary summary = summaries.get(nodeId);
            
            System.out.printf("%s %d %d %d %d %d%n",
                nodeId,
                summary.getMessagesSent(),
                summary.getMessagesReceived(),
                summary.getSumSentMessages(),
                summary.getSumReceivedMessages(),
                summary.getMessagesRelayed()
            );
            
            totalSent += summary.getMessagesSent();
            totalReceived += summary.getMessagesReceived();
            totalSumSent += summary.getSumSentMessages();
            totalSumReceived += summary.getSumReceivedMessages();
        }
        
        // Print totals
        System.out.printf("sum %d %d %d %d%n",
            totalSent,
            totalReceived,
            totalSumSent,
            totalSumReceived
        );
    }
    
    public synchronized Map<String, TrafficSummary> getSummaries() {
        return new HashMap<>(summaries);
    }
}