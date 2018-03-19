package socs.network.node;

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

    //Helper method to calculate the number of ports that are full for a given router
    public int howMany() {
        for(int i = 0; i < 4; i++) {
            if (ports[i] == null) {
                return i;
            }
        }
        return -1;
    }

    //Helper method to process incoming packets.
    public RouterDescription doesExist(SOSPFPacket incomingPacket) {
        int number = howMany();
        for(int i = 0; i < number; i++) {
          if (ports[i].router2.simulatedIPAddress.equals(incomingPacket.srcIP)) {
            if (ports[i].router2.status == RouterStatus.INIT) {
              ports[i].router2.status = RouterStatus.TWO_WAY;
              System.out.println("set " + ports[i].router2.simulatedIPAddress + " state to TWO_WAY");
            }
            return ports[i].router2;
          }
        }
        //If we've gotten to this stage, the link doesn't exist, and therefore a new one needs to be created
        if(number != -1) {
          RouterDescription newRouter = new RouterDescription();
          newRouter.processIPAddress = incomingPacket.srcProcessIP;
          newRouter.processPortNumber = incomingPacket.srcProcessPort;
          newRouter.simulatedIPAddress = incomingPacket.srcIP;
          newRouter.status = RouterStatus.INIT;
          System.out.println("set " + incomingPacket.srcIP + " state to INIT");
          Link newLink = new Link(rd, newRouter, incomingPacket.srcProcessWeight, false);
          ports[number] = newLink;
          return newRouter;
        }
        System.out.println("Link cannot be made, ports full"); 
        return null;
      }

    //Helper method to create LSP packets.
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
      //Initialize serverSocket here so it can be closed.
      ServerSocket serverSocket = null;
      try{
        serverSocket = new ServerSocket(port);
        while(true){
          Socket server = serverSocket.accept();
          //Packet will be recevied due to socket connection, therefore declare input stream.
          ObjectInputStream in = new ObjectInputStream(server.getInputStream());
          //Declare received packet
          SOSPFPacket receivedPacket = (SOSPFPacket) in.readObject();
          //If type 0, hello is received 
          if (receivedPacket.sospfType == 0) {
            System.out.println("Received HELLO from " + receivedPacket.srcIP);
            doesExist(receivedPacket);
            //Send outgoingPacket
            SOSPFPacket outgoingPacket = new SOSPFPacket();
            outgoingPacket.dstIP = receivedPacket.srcIP;
            outgoingPacket.srcProcessIP = "127.0.0.1";
            outgoingPacket.srcProcessPort = port;
            int number = howMany();
            outgoingPacket.srcProcessWeight = ports[number-1].linkWeight;
            outgoingPacket.neighborID = rd.simulatedIPAddress;
            outgoingPacket.srcIP = rd.simulatedIPAddress;
            outgoingPacket.sospfType = 0;
            ObjectOutputStream out = new ObjectOutputStream(server.getOutputStream());
            out.writeObject(outgoingPacket);
            //Should receive another packet confirming two way communication. Pause on in.readObject()
            receivedPacket = (SOSPFPacket) in.readObject();
            if (receivedPacket.sospfType == 0) {
              System.out.println("Received HELLO from " + receivedPacket.srcIP);
            }
            RouterDescription newRouter = doesExist(receivedPacket);
            //Create new LinkDescription for linkStateDatabase
            LinkDescription newLinkDescription = new LinkDescription();
            newLinkDescription.linkID = newRouter.simulatedIPAddress;
            newLinkDescription.portNum = newRouter.processPortNumber;
            newLinkDescription.linkWeight = receivedPacket.srcProcessWeight;

            //Update LSD and send out updated packet.
            lsd._store.get(rd.simulatedIPAddress).links.add(newLinkDescription);
            lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber++;
            
            //Send to all neighbors.
            for(int i = 0; i < number; i++) {
                SOSPFPacket packet = createLSPPacket(ports[i].router2, ports[i]);
                Socket socket = new Socket(InetAddress.getLocalHost(), ports[i].router2.processPortNumber);
                ObjectOutputStream lspOut = new ObjectOutputStream(socket.getOutputStream());
                lspOut.writeObject(packet);
                lspOut.close();
                socket.close(); 
            }
          }
          else { //received a sospfType of 1, which means an LSAUPDATE.

            boolean changes = false;
            //Update LSA
            for (LSA lsa : receivedPacket.lsaArray) {
              if (lsd._store.containsKey(lsa.linkStateID)) {
                if (lsd._store.get(lsa.linkStateID).lsaSeqNumber < lsa.lsaSeqNumber) {
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
                //Create new LSP packet to send to all neighbors except the one it received the LSP from.
                if (!ports[i].router2.simulatedIPAddress.equals(receivedPacket.srcIP)) {
                  SOSPFPacket sendPacket = createLSPPacket(ports[i].router2, ports[i]);
                  Socket socket = new Socket(InetAddress.getLocalHost(), ports[i].router2.processPortNumber);
                  ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                  out.writeObject(sendPacket);
                  out.close();
                  socket.close();
                }
              }
            }
            in.close();
          }
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      finally {
        try {
          serverSocket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
        
      }
    }
}