package socs.network.node;

import socs.network.util.Configuration;
import socs.network.message.SOSPFPacket;
import socs.network.message.LinkDescription;
import socs.network.message.LSA;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

public class Server implements Runnable {

    protected static RouterDescription rd;
    private static short port;

    protected static LinkStateDatabase lsd;
    protected static Link[] ports;

    public Server(RouterDescription newRd, LinkStateDatabase newLSD, Link[] newPorts) {
        rd = newRd;
        port = newRd.processPortNumber;
        lsd = newLSD;
        ports = newPorts;
    }

    public int howMany() {
        for(int i = 0; i < 4; i++) {
            if (ports[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public RouterDescription doesExist(SOSPFPacket incomingPacket) {
        int number = howMany();
        for(int i = 0; i < number; i++) {
          if (ports[i].router2.simulatedIPAddress.equals(incomingPacket.srcIP)) {
            if (ports[i].router2.status == RouterStatus.INIT) {
              ports[i].router2.status = RouterStatus.TWO_WAY;
              System.out.println("set " + ports[i].router2.simulatedIPAddress + " state to TWO_WAY");
              return ports[i].router2;
            }
          }
        }
        if(number != -1) {
          RouterDescription newRouter = new RouterDescription();
          newRouter.processIPAddress = incomingPacket.srcProcessIP;
          newRouter.processPortNumber = incomingPacket.srcProcessPort;
          newRouter.simulatedIPAddress = incomingPacket.srcIP;
          newRouter.status = RouterStatus.INIT;
          System.out.println("set " + incomingPacket.srcIP + " state to INIT");
          Link newLink = new Link(rd, newRouter, incomingPacket.srcProcessWeight, false);
          LinkDescription newLinkDescription = new LinkDescription();
          newLinkDescription.linkID = number + "";
          newLinkDescription.portNum = newRouter.processPortNumber;
          lsd._store.get(rd.simulatedIPAddress).links.add(newLinkDescription);
          ports[number] = newLink;
          return newRouter;
        }
        System.out.println("Link cannot be made, ports full"); 
        return null;
      }

    public static SOSPFPacket createLSPPacket(RouterDescription receivingRouter, Link link) {
        SOSPFPacket packetToReturn = new SOSPFPacket();
        packetToReturn.srcIP= rd.simulatedIPAddress;
        packetToReturn.dstIP = receivingRouter.simulatedIPAddress;
        for (LSA lsa: lsd._store.values()) {
            packetToReturn.lsaArray.add(lsa);
        }
        packetToReturn.neighborID = rd.simulatedIPAddress;
        packetToReturn.sospfType = 1;
        packetToReturn.srcProcessWeight = link.linkWeight;
        return packetToReturn;
    }

    @Override
    public void run() {
        try{
            ServerSocket serverSocket = new ServerSocket(port);
            while(true){
              Socket server = serverSocket.accept();
              try {
                ObjectInputStream in = new ObjectInputStream(server.getInputStream());
                SOSPFPacket receivedPacket = (SOSPFPacket) in.readObject();
                if (receivedPacket.sospfType == 0) {
                  System.out.println("Received HELLO from " + receivedPacket.srcIP);
                  doesExist(receivedPacket);
                  SOSPFPacket outgoingPacket = new SOSPFPacket();
                  outgoingPacket.dstIP = receivedPacket.srcIP;
                  outgoingPacket.srcProcessIP = "127.0.0.1";
                  outgoingPacket.srcProcessPort = port;
                  outgoingPacket.neighborID = rd.simulatedIPAddress;
                  outgoingPacket.srcIP = rd.simulatedIPAddress;
                  outgoingPacket.sospfType = 0;
                  ObjectOutputStream out = new ObjectOutputStream(server.getOutputStream());
                  out.writeObject(outgoingPacket);
                  receivedPacket = (SOSPFPacket) in.readObject();
                  if (receivedPacket.sospfType == 0) {
                    System.out.println("Received HELLO from " + receivedPacket.srcIP);
                  }
                  doesExist(receivedPacket);
                  out.close();
                }
                else { //received a sospfType of 1, which means an LSAUPDATE.
                  System.out.println("Woo");
                  boolean changes = false;
                  for (LSA lsa : receivedPacket.lsaArray) {
                    if (lsd._store.containsKey(lsa.linkStateID)) {
                      if (lsd._store.get(lsa.linkStateID).lsaSeqNumber != lsa.lsaSeqNumber) {
                        lsd._store.put(lsa.linkStateID, lsa);
                        changes = true;
                      }
                    }
                    else {
                      lsd._store.put(lsa.linkStateID, lsa);
                      changes = true;
                    }
                  }
                  if (changes) {
                    int number = howMany();
                    for (int i = 0; i < number; i++) {
                      SOSPFPacket sendPacket = createLSPPacket(ports[i].router2, ports[i]);
                      Socket socket = new Socket(InetAddress.getLocalHost(), ports[i].router2.processPortNumber);
                      ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                      out.writeObject(sendPacket);
                      out.close();
                      socket.close();
                    }
                  }
                }
              }
              catch(Exception e) {
                e.printStackTrace();
              }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}