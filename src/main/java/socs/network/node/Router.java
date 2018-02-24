package socs.network.node;

import socs.network.util.Configuration;
import socs.network.message.SOSPFPacket;
import socs.network.message.LinkDescription;
import socs.network.message.LSA;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();
  int portIndex;

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    lsd = new LinkStateDatabase(rd);
    final short port = config.getShort("port");
    rd.processPortNumber = port;
    Thread listen = new Thread(new Runnable() {
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
              }
              else { //received a sospfType of 1, which means an LSAUPDATE.
                System.out.println("Woo");
                for (LSA lsa : receivedPacket.lsaArray) {
                  if (lsd._store.containsKey(lsa.linkStateID)) {
                    if (lsd._store.get(lsa.linkStateID).lsaSeqNumber != lsa.lsaSeqNumber) {
                      lsd._store.put(lsa.linkStateID, lsa);
                    }
                  }
                  else {
                    lsd._store.put(lsa.linkStateID, lsa);
                  }
                }
              }
            }
            catch(Exception e) {
              e.printStackTrace();
            }
          }   
        }
        catch(Exception e) {
          e.printStackTrace();
        }
      }
    });
    listen.start();
    portIndex = 0;
  }

  public RouterDescription doesExist(SOSPFPacket incomingPacket) {
    for(int i = 0; i < portIndex; i++) {
      if (ports[i].router2.simulatedIPAddress.equals(incomingPacket.srcIP)) {
        if (ports[i].router2.status == RouterStatus.INIT) {
          ports[i].router2.status = RouterStatus.TWO_WAY;
          System.out.println("set " + ports[i].router2.simulatedIPAddress + " state to TWO_WAY");
          return ports[i].router2;
        }
      }
    }
    if(portIndex < 4) {
      RouterDescription newRouter = new RouterDescription();
      newRouter.processIPAddress = incomingPacket.srcProcessIP;
      newRouter.processPortNumber = incomingPacket.srcProcessPort;
      newRouter.simulatedIPAddress = incomingPacket.srcIP;
      newRouter.status = RouterStatus.INIT;
      System.out.println("set " + incomingPacket.srcIP + " state to INIT");
      Link newLink = new Link(rd, newRouter, incomingPacket.srcProcessWeight, false);
      LinkDescription newLinkDescription = new LinkDescription();
      newLinkDescription.linkID = portIndex + "";
      newLinkDescription.portNum = newRouter.processPortNumber;
      lsd._store.get(rd.simulatedIPAddress).links.add(newLinkDescription);
      ports[portIndex] = newLink;
      portIndex++;
      return newRouter;
    }
    System.out.println("Link cannot be made, ports full"); 
    return null;
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {

  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to identify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort, String simulatedIP, short weight) {
    if (portIndex < 4) {
      RouterDescription router2 = new RouterDescription();
      router2.processIPAddress = "127.0.0.1";
      router2.processPortNumber = processPort;
      router2.simulatedIPAddress = simulatedIP;
      Link newLink = new Link(rd, router2, weight, true);
      ports[portIndex] = newLink;
      portIndex++;
    }
    else {
      System.out.println("Ports full for router: " + rd.simulatedIPAddress);
    }
  }

  //Set up SOSPFPacket for LSAUPDATE
  public SOSPFPacket createLSPPacket(RouterDescription receivingRouter, Link link) {
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

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
    for (int i = 0; i < portIndex; i++) {
      final Link port = ports[i];
      Thread broadcast = new Thread(new Runnable() {
        @Override
        public void run() {
          try{
            Socket socketConnection = new Socket(InetAddress.getLocalHost(), port.router2.processPortNumber);
            ObjectOutputStream out = new ObjectOutputStream(socketConnection.getOutputStream());
            SOSPFPacket outgoingPacket = new SOSPFPacket();
            outgoingPacket.dstIP = port.router2.simulatedIPAddress;
            outgoingPacket.srcIP = port.router1.simulatedIPAddress;
            outgoingPacket.srcProcessIP = "127.0.0.1";
            outgoingPacket.srcProcessPort = port.router1.processPortNumber;
            outgoingPacket.neighborID = port.router1.simulatedIPAddress;
            outgoingPacket.sospfType = 0;
            port.router2.status = RouterStatus.INIT;
            out.writeObject(outgoingPacket);
            ObjectInputStream in = new ObjectInputStream(socketConnection.getInputStream());
            SOSPFPacket incomingPacket = (SOSPFPacket) in.readObject();
            System.out.println();
            System.out.println("received HELLO from " + incomingPacket.srcIP);
            if (port.router2.simulatedIPAddress.equals(incomingPacket.srcIP)){
              port.router2.status = RouterStatus.TWO_WAY;
              System.out.println("set " + incomingPacket.srcIP + " state to TWO_WAY");
              out.writeObject(outgoingPacket);
              out.close();
              in.close();
              socketConnection.close();
              LinkDescription newLinkDescription = new LinkDescription();
              //Create new link, update LSA, send all LSA's to all neighboring nodes.
              newLinkDescription.linkID = port.router2.simulatedIPAddress;
              newLinkDescription.portNum = port.router2.processPortNumber;
              newLinkDescription.tosMetrics = port.linkWeight;
              //Update LSA
              lsd._store.get(rd.simulatedIPAddress).links.add(newLinkDescription);
              //TODO, for each neighbor, including the one you just created the link for, send the updated LSP.
              for(int i = 0; i < portIndex; i++) {
                SOSPFPacket packet = createLSPPacket(ports[i].router2, ports[i]);
                Socket socket = new Socket(InetAddress.getLocalHost(), ports[i].router2.processPortNumber);
                ObjectOutputStream lspOut = new ObjectOutputStream(socket.getOutputStream());
                lspOut.writeObject(packet);
                lspOut.close();
                socket.close();
              }
            }
          }
          catch(Exception e){
            e.printStackTrace();
          }
        }
      });
      broadcast.start();
    }
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {

  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
    System.out.println("Printing Neighbors for router: " + rd.simulatedIPAddress);
    for (int i = 0; i < portIndex; i++) {
      if(ports[i].router2.status == RouterStatus.TWO_WAY){
        System.out.println("neighbor " + (i+1) + " [IP ADDRESS]: " + ports[i].router2.simulatedIPAddress);
      }
    }
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.equals("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else {
          //invalid command
          break;
        }
        System.out.print(">> ");
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
