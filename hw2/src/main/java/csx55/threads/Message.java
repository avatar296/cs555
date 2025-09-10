package csx55.threads;

public class Message {
  public enum Type {
    REGISTER,
    OVERLAY_INFO,
    TASK_COUNT,
    TASK_MIGRATION,
    RESULTS
  }

  private Type type;
  private String payload;

  public Message(Type type, String payload) {
    this.type = type;
    this.payload = payload;
  }

  public Type getType() {
    return type;
  }

  public String getPayload() {
    return payload;
  }

  @Override
  public String toString() {
    return type + ":" + payload;
  }
}
