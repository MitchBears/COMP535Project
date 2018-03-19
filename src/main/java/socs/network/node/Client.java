package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.message.LinkDescription;

import java.io.*;
import java.net.Socket;
import java.net.InetAddress;

public class Client extends Server {

    private final Link port;

    public Client (RouterDescription newRd, LinkStateDatabase newLSD, Link[] newPorts, Link newPort) {
        super(newRd, newLSD, newPorts);
        port = newPort;
    }

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
            outgoingPacket.srcProcessWeight = port.linkWeight;
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
                //Close input streams and socketconnection, all communication through this socket is done.
                out.close();
                in.close();
                socketConnection.close();
                LinkDescription newLinkDescription = new LinkDescription();
                //Create new link, update LSA, send LSP to all neighboring nodes.
                newLinkDescription.linkID = port.router2.simulatedIPAddress;
                newLinkDescription.portNum = port.router2.processPortNumber;
                newLinkDescription.linkWeight = port.linkWeight;
                lsd._store.get(rd.simulatedIPAddress).links.add(newLinkDescription);
                lsd._store.get(rd.simulatedIPAddress).lsaSeqNumber++;
                int number = howMany();
                for(int i = 0; i < number; i++) {
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
}