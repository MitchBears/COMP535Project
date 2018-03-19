package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

public class LinkStateDatabase {

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;
  private WeightedGraph graph;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }


  boolean hasUnvisitedNeighbors(int index, HashSet<Integer> unvisited) {
    for (int i = 0; i < graph.edges.length; i++) {
      if (i != index) {
        if (graph.edges[index][i] != 0 && unvisited.contains(i)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  String getShortestPath(String destinationIP) {
    //Weighted graph
    graph = new WeightedGraph(this);
    //Distance from this router to every other router (including itself)
    short [] distance = new short[graph.edges.length];
    //Predecessor for each router in the Dijkstra's shortest path algorithm
    short [] predecessor = new short[graph.edges.length];
    //Boolean array to indicate whether or not the node has been visited.
    boolean [] visited = new boolean[graph.edges.length];

    //Initialize
    for (int i = 0; i < graph.edges.length; i++) {
      visited[i] = false;
      distance[i] = Short.MAX_VALUE;
      predecessor[i] = -1;
    }

    //Current index is the index of the router we're getting the shortest path from.
    int currentIndex = graph.getIndex(rd.simulatedIPAddress);
    //Distance from the current router to itself is 0.
    distance[currentIndex] = 0;

    //Dijstra's shortest path algorithm
    int next = 0;
    for (int i = 0; i < graph.edges.length; i++) {
      short minimum = Short.MAX_VALUE;
      for (int j = 0; j < graph.edges.length; j++) {
        if (minimum > distance[j] && visited[j] == false) {
          minimum = distance[j];
          next = j;
        }
      }
      visited[next] = true;
      for (int j = 0; j < graph.edges.length; j++) {
        if (!visited[j] && graph.edges[next][j] != 0) {
          if (minimum + graph.edges[next][j] < distance[j]) {
            distance[j] = (short)(minimum + graph.edges[next][j]);
            predecessor[j] = (short)next;
          }
        }
      }
    }

    //Build return string through predecessor node traversal.
    StringBuilder returnString = new StringBuilder();
    //Stack for predecessor traversal
    Stack<Integer> sequence = new Stack<Integer>();
    //currentIndex represents the index of the node that we're traversing to from the current node.
    currentIndex = graph.getIndex(destinationIP);
    //Traverse the graph backwards from the node we're trying to get the shortest path to
    while(predecessor[currentIndex] != -1) {
      sequence.push(currentIndex);
      currentIndex = predecessor[currentIndex];
    }
    sequence.push(currentIndex);
    //Create return string that represents the shortest path to the node of interest.
    if (sequence.peek() == graph.getIndex(rd.simulatedIPAddress)) {
      returnString.append(graph.IDs[sequence.pop()]);
      int previous = 0;
      int current = graph.getIndex(rd.simulatedIPAddress);
      while (!sequence.isEmpty()) {
        previous = current;
        current = sequence.pop();
        returnString.append("->" + "(" + graph.edges[previous][current] + ")" + " " + graph.IDs[current]);
      }
    }
    return returnString.toString();
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = 0;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    ld.linkWeight = 0;
    lsa.links.add(ld);
    return lsa;
  }


  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                append(ld.linkWeight).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
