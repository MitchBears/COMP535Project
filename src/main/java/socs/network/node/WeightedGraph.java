package socs.network.node;

import socs.network.message.LSA;


public class WeightedGraph {

    short[][] edges;
    String[] IDs;
    public WeightedGraph(LinkStateDatabase newLSD) {
        int length = newLSD._store.keySet().size();
        edges = new short[length][length];
        IDs = new String[length];
        for (LSA lsa : newLSD._store.values()) {

        }
    }


}

