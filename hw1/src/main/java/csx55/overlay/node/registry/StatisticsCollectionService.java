package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.StatisticsCollector;
import csx55.overlay.wireformats.TrafficSummary;

/**
 * Service for collecting and displaying statistics from messaging nodes
 */
public class StatisticsCollectionService {
    private StatisticsCollector statsCollector = null;
    
    public synchronized void reset(int nodeCount) {
        statsCollector = new StatisticsCollector(nodeCount);
    }
    
    public synchronized void handleTrafficSummary(TrafficSummary summary, TCPConnection connection) {
        if (statsCollector == null) {
            System.err.println("Statistics collector not initialized");
            return;
        }
        
        String nodeId = summary.getNodeId();
        
        statsCollector.addSummary(summary);
        System.out.println("Received traffic summary from: " + nodeId);
        
        if (statsCollector.hasAllSummaries()) {
            printFinalStatistics();
        }
    }
    
    private void printFinalStatistics() {
        if (statsCollector != null) {
            System.out.println("\nFinal Statistics:");
            System.out.println("=================");
            statsCollector.printStatistics();
            statsCollector.reset();
        }
    }
    
    public StatisticsCollector getStatsCollector() {
        return statsCollector;
    }
}