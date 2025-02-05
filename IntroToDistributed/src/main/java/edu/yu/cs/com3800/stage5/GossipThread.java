package edu.yu.cs.com3800.stage5;
package edu.yu.cs.com3800.stage5;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.PeerServer;
import edu.yu.cs.com3800.Vote;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.Random;
import java.util.logging.Logger;

public class GossipThread extends Thread implements GossipLoggingServer {
    static final int GOSSIP = 3000/2;
    static final int FAIL = GOSSIP * 10; // in milliseconds
    static final int Cleanup = FAIL * 2;

    private LinkedBlockingQueue<Message> incomingMessages;
    private LinkedBlockingQueue<Message> outgoingMessages;
    private Map<Long, InetSocketAddress> peerIDtoAddress;
    private long myID;
    private PeerServerImpl myPeerServer;
    private Map<Long, Long> peerIDtoTime;
    private List<InetSocketAddress> serverAddresses;
    private List<InetSocketAddress> deadServers;
    private Map<Long, Long> gossipMap;
    private List<InetSocketAddress> liveServers;
    volatile long heartbeat = 0;
    private Map<InetSocketAddress, Long> addressToId;
    int myPort;
    int httpPort;
    HttpServer httpServer;
    Random random;
    Logger verboseLogger;
    Logger summaryLogger;
    volatile boolean shutdown = false;
    private ScheduledExecutorService scheduledExecutorService;




    public GossipThread(LinkedBlockingQueue<Message> incomingMessages, LinkedBlockingQueue<Message> outgoingMessages, Map<Long, InetSocketAddress> peerIDtoAddress, long myID, PeerServerImpl peerServer, List<InetSocketAddress> deadServers) {
        this.incomingMessages = incomingMessages;
        this.outgoingMessages = outgoingMessages;
        this.peerIDtoAddress = peerIDtoAddress;
        this.myID = myID;
        this.myPeerServer = peerServer;
        this.peerIDtoTime = new HashMap<>();
        this.serverAddresses = new ArrayList<>();
        this.deadServers = deadServers;
        this.scheduledExecutorService = Executors.newScheduledThreadPool(1);
        this.myPort = this.myPeerServer.getAddress().getPort();
        try {
            this.verboseLogger = initializeLogging(GossipThread.class.getCanonicalName() + "-verbose-on-port-" + myPort, true);
            this.summaryLogger = initializeLogging(GossipThread.class.getCanonicalName() + "-summary-on-port-" + myPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for(InetSocketAddress address : peerIDtoAddress.values()) {
            this.serverAddresses.add(address);
        }
        this.gossipMap = new ConcurrentHashMap<>();
        long startTime = System.currentTimeMillis();
        /*for(long id : peerIDtoAddress.keySet()) {
            this.peerIDtoTime.put(id, startTime);
            this.gossipMap.put(id, 0L);
        }*/
        peerIDtoTime.put(myID, startTime);
        gossipMap.put(myID, 0L);
        this.random = new Random();
        this.addressToId = new ConcurrentHashMap<>();
        for(long id : this.peerIDtoAddress.keySet()) {
            this.addressToId.put(this.peerIDtoAddress.get(id), id);
        }
        this.httpPort = this.myPort + 4;
        try {
            this.httpServer = HttpServer.create(new InetSocketAddress(this.myPeerServer.getAddress().getAddress(), this.httpPort), 0);
            this.httpServer.createContext("/summary", new SummaryHandler());
            this.httpServer.createContext("/verbose", new VerboseHandler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run(){
        long initialWait = 500;
        long maxWait = 30000;
        this.httpServer.start();
        sendGossip();
        while(!shutdown) {
            try {
                Message message = incomingMessages.poll(initialWait, TimeUnit.MILLISECONDS);
                if(message != null){
                   if(message.getMessageType() != Message.MessageType.GOSSIP){
                       this.outgoingMessages.offer(message);
                       continue;
                   }
                   long receivedTime = System.currentTimeMillis();
                   byte[] gossipBytes = message.getMessageContents();
                   Map<Long, Long> recievedMap = deserializeMap(gossipBytes);
                   verboseLogger.info("Received gossip from " + message.getSenderHost() + ":" + message.getSenderPort() + " at time: " + receivedTime + " with contents: " + recievedMap);
                   for(HashMap.Entry<Long, Long> entry : recievedMap.entrySet()){
                       if(entry.getKey() == this.myID){
                           continue; //skip myself
                       }
                       Long idInQuestion = entry.getKey();
                       Long heartBeatForID = entry.getValue();
                       //if marked dead already ignore, so as not to rediscover dead nodes
                       if(this.deadServers.contains(this.peerIDtoAddress.get(idInQuestion))){
                           continue;
                       }
                       //discover nodes
                       if(!this.gossipMap.containsKey(idInQuestion)){
                           this.gossipMap.put(idInQuestion, heartBeatForID);
                           this.peerIDtoTime.put(idInQuestion, receivedTime);
                           continue;
                       }
                       Long currentHeartBeat = this.gossipMap.get(idInQuestion);
                       //has the node already
                       if(currentHeartBeat >= heartBeatForID){
                           if(receivedTime - peerIDtoTime.get(idInQuestion) > FAIL){
                                 InetSocketAddress removedAddress = this.peerIDtoAddress.get(idInQuestion);
                               //this.gossipMap.remove(idInQuestion);
                               //InetSocketAddress removedAddress = this.peerIDtoAddress.remove(idInQuestion);
                               if(!this.deadServers.contains(removedAddress)){
                                   synchronized (this.deadServers) {
                                       this.deadServers.add(removedAddress);
                                   }
                               }
                               synchronized (this.serverAddresses){
                                   this.serverAddresses.remove(removedAddress);
                               }
                               this.summaryLogger.info(this.myID + ": no heartbeat from server " + idInQuestion + " - SERVER FAILED");
                               System.out.println(this.myID + ": no heartbeat from server " + idInQuestion + " - SERVER FAILED");

                               Vote currentLeader = this.myPeerServer.getCurrentLeader();
                               PeerServer.ServerState state = this.myPeerServer.getPeerState();
                               if(currentLeader.getProposedLeaderID() == idInQuestion){
                                   this.myPeerServer.peerEpoch++;
                                   if(state != PeerServer.ServerState.OBSERVER){
                                       this.myPeerServer.setPeerState(PeerServer.ServerState.LOOKING);
                                       this.summaryLogger.info(this.myID + ": switching from " + state + " to " + PeerServer.ServerState.LOOKING);
                                       System.out.println(this.myID + ": switching from " + state + " to " + PeerServer.ServerState.LOOKING);
                                   }else{
                                       this.myPeerServer.made = false;
                                   }
                               }
                           }
                       }
                       else{
                            this.gossipMap.put(idInQuestion, heartBeatForID);
                            this.peerIDtoTime.put(idInQuestion, receivedTime);
                           // this.summaryLogger.info(this.myID + ": updated " + idInQuestion + "'s heartbeat sequence to " + heartBeatForID + " based on message from " + this.addressToId.get(new InetSocketAddress(message.getSenderHost(), message.getSenderPort())) + " at node time " + receivedTime);
                       }
                   }
                }
                initialWait = Math.min(maxWait, initialWait * 2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    private void sendGossip(){
        this.scheduledExecutorService.scheduleAtFixedRate(() -> {
            Map<Long, Long> gossipMapCopy = new HashMap<>(this.gossipMap);
            gossipMapCopy.put(this.myID, ++this.heartbeat);
            byte[] gossipBytes = this.serializeMap(gossipMapCopy);
            synchronized (this.serverAddresses){
                int random = this.random.nextInt(this.serverAddresses.size());
                this.myPeerServer.sendMessage(Message.MessageType.GOSSIP, gossipBytes, this.serverAddresses.get(random));
            }
        }, 0, GOSSIP, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    private byte[] serializeMap(Map<Long, Long> map){
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(map);
            objectOutputStream.flush();
            objectOutputStream.close();
            byteArrayOutputStream.close();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private Map<Long, Long> deserializeMap(byte[] bytes){
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            @SuppressWarnings("unchecked")
            Map<Long, Long> toReturn = (Map<Long, Long>) objectInputStream.readObject();
            objectInputStream.close();
            byteArrayInputStream.close();
            return toReturn;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    public void shutdown() {
        this.httpServer.stop(0);
        this.shutdown = true;
        this.scheduledExecutorService.shutdown();
    }



    private class SummaryHandler implements HttpHandler{

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("Summary handler called");
            String fileName = GossipThread.class.getCanonicalName() + "-summary-on-port-" + myPort + ".log";
            Path path = Paths.get(dirName ,fileName);
            byte[] fileBytes = Files.readAllBytes(path);
            exchange.sendResponseHeaders(200, fileBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();
        }
    }

    private class VerboseHandler implements HttpHandler{

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String fileName = GossipThread.class.getCanonicalName() + "-verbose-on-port-" + myPort + ".log";
            Path path = Paths.get(dirName, fileName);
            byte[] fileBytes = Files.readAllBytes(path);
            exchange.sendResponseHeaders(200, fileBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();
        }
    }




}
