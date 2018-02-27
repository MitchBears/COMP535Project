package socs.network.node;

import socs.network.util.Configuration;

import java.io.*;

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
    Thread listen = new Thread(new Server(rd, lsd, ports));
    listen.start();
    portIndex = 0;
  }

  public int howMany() {
    for(int i = 0; i < 4; i++) {
        if (ports[i] == null) {
            return i;
        }
    }
    return -1;
  } 
  

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {
    WeightedGraph graph = new WeightedGraph(lsd);
    System.out.println(graph.toString());
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
    int number = howMany();
    if (number < 4) {
      RouterDescription router2 = new RouterDescription();
      router2.processIPAddress = "127.0.0.1";
      router2.processPortNumber = processPort;
      router2.simulatedIPAddress = simulatedIP;
      Link newLink = new Link(rd, router2, weight, true);
      ports[number] = newLink;
    }
    else {
      System.out.println("Ports full for router: " + rd.simulatedIPAddress);
    }
  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
    int number = howMany();
    for (int i = 0; i < number; i++) {
      if (ports[i].attached) {
        Link port = ports[i];
        Thread broadcast = new Thread(new Client(rd, lsd, ports, port));
        broadcast.start();
      }
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
