package csx55.overlay.routing;

/**
 * Represents an edge in the overlay network graph. An edge consists of a destination node and the
 * weight of the connection. Used for routing table construction and path finding algorithms.
 */
public class Edge {
  private final String destination;
  private final int weight;

  /**
   * Constructs a new Edge with the specified destination and weight.
   *
   * @param destination the identifier of the destination node (format: "IP:port")
   * @param weight the weight/cost of the edge connection
   */
  public Edge(String destination, int weight) {
    this.destination = destination;
    this.weight = weight;
  }

  /**
   * Gets the destination node identifier.
   *
   * @return the destination node identifier
   */
  public String getDestination() {
    return destination;
  }

  /**
   * Gets the weight of the edge connection.
   *
   * @return the weight/cost of the edge
   */
  public int getWeight() {
    return weight;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    Edge edge = (Edge) obj;
    return weight == edge.weight
        && (destination != null ? destination.equals(edge.destination) : edge.destination == null);
  }

  @Override
  public int hashCode() {
    int result = destination != null ? destination.hashCode() : 0;
    result = 31 * result + weight;
    return result;
  }

  @Override
  public String toString() {
    return "Edge{destination='" + destination + "', weight=" + weight + "}";
  }
}
