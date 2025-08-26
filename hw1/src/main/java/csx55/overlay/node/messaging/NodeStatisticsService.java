package csx55.overlay.node.messaging;

import csx55.overlay.transport.TCPConnection;
import csx55.overlay.wireformats.TrafficSummary;

import java.io.IOException;

/**
 * Service for tracking node statistics
 */
public class NodeStatisticsService {
    private int sendTracker = 0;
    private int receiveTracker = 0;
    private int relayTracker = 0;
    private long sendSummation = 0;
    private long receiveSummation = 0;
    
    public synchronized void incrementSendStats(int payload) {
        sendTracker++;
        sendSummation += payload;
    }
    
    public synchronized void incrementReceiveStats(int payload) {
        receiveTracker++;
        receiveSummation += payload;
    }
    
    public synchronized void incrementRelayStats() {
        relayTracker++;
    }
    
    public synchronized void sendTrafficSummary(String nodeIp, int nodePort, TCPConnection registryConnection) throws IOException {
        TrafficSummary summary = new TrafficSummary(
            nodeIp,
            nodePort,
            sendTracker,
            sendSummation,
            receiveTracker,
            receiveSummation,
            relayTracker
        );
        
        registryConnection.sendEvent(summary);
        System.out.println("Sent traffic summary to registry");
        // Don't reset counters here - they should be reset only when a new task starts
    }
    
    public synchronized void resetCounters() {
        sendTracker = 0;
        receiveTracker = 0;
        relayTracker = 0;
        sendSummation = 0;
        receiveSummation = 0;
    }
    
    public synchronized int getSendTracker() {
        return sendTracker;
    }
    
    public synchronized int getReceiveTracker() {
        return receiveTracker;
    }
}