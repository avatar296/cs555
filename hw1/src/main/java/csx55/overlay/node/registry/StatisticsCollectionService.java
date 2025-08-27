package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.LoggerUtil;
import csx55.overlay.util.StatisticsCollector;
import csx55.overlay.wireformats.TrafficSummary;

/**
 * Service responsible for collecting and displaying traffic statistics from messaging nodes.
 * Aggregates traffic summaries from all nodes and presents consolidated statistics
 * after all nodes have reported.
 * 
 * This service maintains thread-safe access to the statistics collector
 * and handles the complete statistics lifecycle.
 */
public class StatisticsCollectionService {
    private StatisticsCollector statsCollector = null;
    
    /**
     * Resets the statistics collector with the expected number of nodes.
     * Prepares the collector to receive summaries from all nodes.
     * 
     * @param nodeCount the number of nodes expected to report statistics
     */
    public synchronized void reset(int nodeCount) {
        statsCollector = new StatisticsCollector(nodeCount);
    }
    
    /**
     * Handles a traffic summary report from a messaging node.
     * Adds the summary to the collector and prints statistics when all nodes have reported.
     * 
     * @param summary the traffic summary from a node
     * @param connection the TCP connection from the reporting node
     */
    public synchronized void handleTrafficSummary(TrafficSummary summary, TCPConnection connection) {
        if (statsCollector == null) {
            LoggerUtil.error("StatisticsCollection", "Statistics collector not initialized - cannot handle traffic summary");
            return;
        }
        
        String nodeId = summary.getNodeId();
        
        statsCollector.addSummary(summary);
        
        if (statsCollector.hasAllSummaries()) {
            printFinalStatistics();
        }
    }
    
    /**
     * Prints the final aggregated statistics to the console.
     * Displays the summary after all nodes have reported their traffic.
     */
    private void printFinalStatistics() {
        if (statsCollector != null) {
            statsCollector.printStatistics();
            statsCollector.reset();
        }
    }
    
    /**
     * Gets the current statistics collector.
     * 
     * @return the statistics collector instance, or null if not initialized
     */
    public StatisticsCollector getStatsCollector() {
        return statsCollector;
    }
}