package socs.network.node;

public class Link {

  boolean attached;
  RouterDescription router1;
  RouterDescription router2;
  short linkWeight;

  public Link(RouterDescription r1, RouterDescription r2, short weight, boolean attach) {
    router1 = r1;
    router2 = r2;
    linkWeight = weight;
    attached = attach;
  }
}
