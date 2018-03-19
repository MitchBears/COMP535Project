package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;


public class WeightedGraph {

    short[][] edges;
    String[] IDs;

    public WeightedGraph(LinkStateDatabase newLSD) {
        int length = newLSD._store.keySet().size();
        edges = new short[length][length];
        IDs = new String[length];
        int idIndex = 0;
        
        for (LSA lsa : newLSD._store.values()) {
            IDs[idIndex] = lsa.linkStateID;
            idIndex++;
        }

        for (LSA lsa : newLSD._store.values()) {
            int index = getIndex(lsa.linkStateID);
            for (LinkDescription linkDescription : lsa.links) {
                int neighborIndex = getIndex(linkDescription.linkID);
                edges[index][neighborIndex] = linkDescription.linkWeight;
            }
        }
    }

    public int getIndex(String simulatedIPAddress) {
        for (int i = 0; i < IDs.length; i++) {
            if (IDs[i].equals(simulatedIPAddress)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();
        for (int i = 0; i < edges.length; i++) {
            string.append("Router: " + IDs[i]);
            for (int j = 0; j < edges[i].length; j++) {
                if (edges[i][j] != 0) {
                    string.append("Link to: " + IDs[j] + " weight: " + edges[i][j] + '\n');
                }
            }
        }
        return string.toString();
    }
}

