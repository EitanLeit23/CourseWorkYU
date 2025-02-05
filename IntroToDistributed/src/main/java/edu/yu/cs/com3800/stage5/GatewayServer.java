package edu.yu.cs.com3800.stage5;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class GatewayServer extends Thread implements LoggingServer {
    private Logger logger;
    private HttpServer httpServer;
    private GatewayPeerServerImpl gatewayPeerServer;
    private ConcurrentHashMap<Integer, byte[]> cache;
    private Vote currentLeader = null;
    private int peerPort;
    private ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress;
    private int leaderPort;
    private InetSocketAddress leaderAddress;
    private InetSocketAddress myAddress;

    private ConcurrentHashMap<Long, Message> messageCache;
    private AtomicLong messageID;


    public GatewayServer(int httpPort, int peerPort, long peerEpoch, Long serverID, ConcurrentHashMap<Long, InetSocketAddress> peerIDtoAddress, int numberOfObservers) throws IOException {
        if (httpPort < 0 || peerPort < 0 || peerEpoch < 0 || serverID < 0 || peerIDtoAddress == null || numberOfObservers < 0) {
            throw new IllegalArgumentException();
        }
        this.logger = initializeLogging(GatewayServer.class.getCanonicalName() + "-on-port-" + peerPort);
        this.peerPort = peerPort;
        this.cache = new ConcurrentHashMap<>();
        this.messageCache = new ConcurrentHashMap<>();
        this.messageID = new AtomicLong(0);
        this.httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        //this.logger.info("starting GatewayPeerServer with port " + peerPort + " and epoch " + peerEpoch + " and serverID " + serverID);
        Map<Long, InetSocketAddress> map = new HashMap<>(peerIDtoAddress);
        this.gatewayPeerServer = new GatewayPeerServerImpl(peerPort, peerEpoch, serverID, map, serverID, numberOfObservers);
        this.gatewayPeerServer.setPeerState(PeerServer.ServerState.OBSERVER);
        this.peerIDtoAddress = peerIDtoAddress;
        this.myAddress = this.peerIDtoAddress.get(serverID);
        this.logger.info("myAdreess" + this.myAddress.getHostString());
        this.httpServer.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        this.httpServer.createContext("/compileandrun", new GatewayHandler());
        this.httpServer.createContext("/getleader", new getLeaderHandler(this.gatewayPeerServer));
        this.setName("GatewayServer-port-" + httpPort);
        this.logger.info("GatewayServer created");
    }
    public void run() {
        this.gatewayPeerServer.start();
        this.logger.info("GatewayPeerServer started");
        //long currentLeaderID = this.gatewayPeerServer.getCurrentLeaderID();
        //this.logger.info("Gateway Server Running");
        //this.currentLeader = this.gatewayPeerServer.getCurrentLeader();
        //this.logger.info("Leader is " + this.currentLeader);
        this.httpServer.start();
        //this.logger.info("GatewayServer started");
        //this.leaderPort = this.gatewayPeerServer.getPeerByID(this.currentLeader.getProposedLeaderID()).getPort() + 2;
        //this.leaderAddress = this.peerIDtoAddress.get(this.currentLeader.getProposedLeaderID());
        //int leaderPort = this.gatewayPeerServer.getPeerByID(this.currentLeader.getProposedLeaderID()).getPort() + 2;
        //InetSocketAddress leaderAddress = this.peerIDtoAddress.get(this.currentLeader.getProposedLeaderID());
        while (!this.isInterrupted()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.logger.info("GatewayServer stopped");
    }
    public void shutdown() throws IOException {
        this.httpServer.stop(0);
        this.gatewayPeerServer.shutdown();
        this.logger.info("Shutdown Initiated");
        this.interrupt();
    }

    public GatewayPeerServerImpl getGatewayPeerServer() {
        return this.gatewayPeerServer;
    }
    private class getLeaderHandler implements HttpHandler{
        GatewayPeerServerImpl gatewayPeerServer;
        ReentrantLock leaderLock;

        public getLeaderHandler(GatewayPeerServerImpl gatewayPeerServer) {
            this.gatewayPeerServer = gatewayPeerServer;
            this.leaderLock = this.gatewayPeerServer.leaderLock;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            this.leaderLock.lock();
            long leaderID = this.gatewayPeerServer.getCurrentLeader().getProposedLeaderID();
            this.leaderLock.unlock();
            String response = leaderID + ": is Leader";
            for(Long id : this.gatewayPeerServer.peerIDtoAddress.keySet()){
                if(id == leaderID){
                    continue;
                }
                if(id == this.gatewayPeerServer.getServerId()){
                    response += "\n" + id + ": is Observer";
                }
                else{
                    response += "\n" + id + ": is Follower";
                }
            }
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        }
    }

    private class GatewayHandler implements HttpHandler, LoggingServer {
        private long leaderID;
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Logger logger = initializeLogging(GatewayHandler.class.getCanonicalName() + "-on-port-" + exchange.getRemoteAddress().getPort());
            logger.info("Handling request");
            if(!exchange.getRequestMethod().equals("POST")){
                String response = "must be POST request";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
                return;
            }
            if(!exchange.getRequestHeaders().getFirst("Content-Type").equals("text/x-java-source")){
                String response = "must be text/x-java-source";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
                return;
            }
            InputStream is = exchange.getRequestBody();
            byte[] requestArray = is.readAllBytes();
            //logger.info("Read request into byte array");
            if(cache.containsKey(Arrays.hashCode(requestArray))){
                logger.info("Request in cache");
                byte[] cachedResponse = cache.get(Arrays.hashCode(requestArray));
                exchange.getResponseHeaders().set("Cached-Response", "true");
                Message msg = new Message(cachedResponse);
                byte[] responseArray = msg.getMessageContents();
                if(msg.getErrorOccurred()){
                    exchange.sendResponseHeaders(400, responseArray.length);
                } else {
                    exchange.sendResponseHeaders(200, responseArray.length);
                }
                exchange.getResponseBody().write(msg.getMessageContents());
                exchange.close();
                return;
            }
            logger.info("Request not in cache");
            gatewayPeerServer.leaderLock.lock();
            this.leaderID = gatewayPeerServer.getCurrentLeader().getProposedLeaderID();
            gatewayPeerServer.leaderLock.unlock();
            boolean received = false;
            while(!received){
                received = sendRequestToLeader(exchange, requestArray);
            }
        }
        private boolean sendRequestToLeader(HttpExchange exchange, byte[] requestArray){
            Socket leaderSocket;
            try{
                //this will wait if there's no new leader yet and return immediately if there is one
                gatewayPeerServer.leaderLock.lock();
                long leaderID = gatewayPeerServer.getCurrentLeader().getProposedLeaderID();
                gatewayPeerServer.leaderLock.unlock();
                InetSocketAddress leaderAddress = peerIDtoAddress.get(leaderID);
                logger.info("Attempting to connect to leader at " + leaderAddress.getAddress() + ":" + (leaderAddress.getPort() + 2));
                leaderSocket = new Socket(leaderAddress.getAddress(), leaderAddress.getPort() + 2);
                logger.info("Connected to leader");
                //send via tcp to leader
                InputStream leaderInputStream;
                OutputStream leaderOutputStream;
                //logger.info("Getting input/output streams from leader");
                leaderInputStream = leaderSocket.getInputStream();
                leaderOutputStream = leaderSocket.getOutputStream();
                //logger.info("Got input/output streams from leader");
                long requestID = messageID.getAndIncrement();
                Message requestMessage = new Message(Message.MessageType.WORK, requestArray, myAddress.getHostString(), peerPort, leaderAddress.getHostName(), leaderAddress.getPort(), requestID);
                messageCache.put(requestID, requestMessage);
                //logger.info("Created message from request " + requestMessage.toString());
                byte[] requestMessageArray = requestMessage.getNetworkPayload();
                //logger.info("Created payload from request");
                //logger.info("Sending request to leader");
                leaderOutputStream.write(intToBytes(requestMessageArray.length));
                leaderOutputStream.write(requestMessageArray);
                leaderOutputStream.flush();
                //leaderOutputStream.close();
                logger.info("Sent request to leader");

                //recieve from leader, cache response, send response to client
                logger.info("waiting for response from leader");
                byte[] responseLengthArray = new byte[4];
                leaderInputStream.read(responseLengthArray);
                int responseLength = bytesToInt(responseLengthArray);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] tempBuffer = new byte[1024];
                int bytesRead;
                int totalBytesRead = 0;

                while ((totalBytesRead < responseLength && (bytesRead = leaderInputStream.read(tempBuffer, 0,Math.min(tempBuffer.length, responseLength - totalBytesRead))) != -1)) {
                    buffer.write(tempBuffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
                byte[] responseArray = buffer.toByteArray();
                //byte[] responseArray = leaderInputStream.readAllBytes();
                logger.info("Recieved response from leader");
                //leaderInputStream.close();
                //leaderSocket.close();
                Message msg = new Message(responseArray);
                byte[] responseMessageArray = msg.getMessageContents();
                cache.put(Arrays.hashCode(requestArray), responseArray);
                messageCache.remove(msg.getRequestID());
                exchange.getResponseHeaders().set("Cached-Response", "false");
                if(msg.getErrorOccurred()){
                    exchange.sendResponseHeaders(400, responseMessageArray.length);
                } else {
                    exchange.sendResponseHeaders(200, responseMessageArray.length);
                }
                leaderInputStream.close();
                leaderOutputStream.close();
                leaderSocket.close();
                exchange.getResponseBody().write(responseMessageArray);
                exchange.close();
                return true;
            }catch(IOException e){
                logger.warning("Failed to connect to leader");
                return false;
            }
        }
        private static byte[] intToBytes(int value) {
            return new byte[]{
                    (byte) (value >> 24),
                    (byte) (value >> 16),
                    (byte) (value >> 8),
                    (byte) value
            };
        }
        private static int bytesToInt(byte[] bytes) {
            return ((bytes[0] & 0xFF) << 24) |
                    ((bytes[1] & 0xFF) << 16) |
                    ((bytes[2] & 0xFF) << 8) |
                    (bytes[3] & 0xFF);
        }
    }
}
