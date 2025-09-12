package csx55.overlay.wireformats;

import java.io.*;
import java.util.*;

public class LinkWeights implements Event {
  public static final class Link {
    public final String a, b;
    public final int w;

    public Link(String a, String b, int w) {
      if (w < 1 || w > 10) {
        throw new IllegalArgumentException("Link weight must be between 1 and 10, got: " + w);
      }
      this.a = a;
      this.b = b;
      this.w = w;
    }
  }

  private final List<Link> links;

  public LinkWeights(List<Link> links) {
    this.links = List.copyOf(links);
  }

  public LinkWeights(DataInputStream in) throws IOException {
    int n = in.readInt();
    List<Link> ls = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      String a = in.readUTF();
      String b = in.readUTF();
      int w = in.readInt();

      if (w < 1 || w > 10) {

        w = Math.max(1, Math.min(10, w));
      }
      ls.add(new Link(a, b, w));
    }
    this.links = Collections.unmodifiableList(ls);
  }

  public List<Link> links() {
    return links;
  }

  @Override
  public int type() {
    return Protocol.LINK_WEIGHTS;
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    out.writeInt(type());
    out.writeInt(links.size());
    for (Link l : links) {
      out.writeUTF(l.a);
      out.writeUTF(l.b);
      out.writeInt(l.w);
    }
  }
}
