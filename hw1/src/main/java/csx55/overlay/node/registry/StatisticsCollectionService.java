package csx55.overlay.node.registry;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.util.LoggerUtil;
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
            LoggerUtil.error("StatisticsCollection", "Statistics collector not initialized - cannot handle traffic summary");
            return;
        }
        
        String nodeId = summary.getNodeId();
        
        statsCollector.addSummary(summary);
        
        if (statsCollector.hasAllSummaries()) {
            printFinalStatistics();
        }
    }
    
    private void printFinalStatistics() {
        if (statsCollector != null) {
            statsCollector.printStatistics();
            statsCollector.reset();
        }
    }
    
    public StatisticsCollector getStatsCollector() {
        return statsCollector;
    }
}