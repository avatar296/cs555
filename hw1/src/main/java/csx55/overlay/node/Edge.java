package csx55.overlay.node;

/**
 * Represents an edge in the overlay network graph.
 * An edge consists of a destination node and the weight of the connection.
 * Used for routing table construction and path finding algorithms.
 */
public class Edge {
    /**
     * The identifier of the destination node (format: "IP:port")
     */
    public String destination;
    
    /**
     * The weight of the edge connection, representing the cost to reach the destination
     */
    public int weight;

    /**
     * Constructs a new Edge with the specified destination and weight.
     * 
     * @param destination the identifier of the destination node
     * @param weight the weight/cost of the edge connection
     */
    public Edge(String destination, int weight) {
        this.destination = destination;
        this.weight = weight;
    }
}