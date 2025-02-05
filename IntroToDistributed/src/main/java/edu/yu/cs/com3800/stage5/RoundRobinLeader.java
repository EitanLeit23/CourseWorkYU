package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class RoundRobinLeader extends Thread implements LoggingServer {

    private PeerServerImpl server;
    private Logger logger;
    private int port;
    private ConcurrentHashMap<Long, InetSocketAddress> requestIDtoAddress;
    private ConcurrentHashMap<Long, Message> messageCache;
    private List<InetSocketAddress> workers;
    private int nextWorkerIndex;
    private ServerSocket gatewayServerSocket;
    private int tcpPort;
    private ExecutorService executor;
    private long gatewayID;
    private List<InetSocketAddress> deadServers;

    public RoundRobinLeader(PeerServerImpl server, int port, List<InetSocketAddress> workers, List<InetSocketAddress> deadServers, long gatewayID) {
        this.server = server;
        this.port = port;
        this.tcpPort = port + 2;
        this.nextWorkerIndex = 0;
        this.requestIDtoAddress = new ConcurrentHashMap<>();
        this.workers = workers;
        this.deadServers = deadServers;
        this.gatewayID = gatewayID;
        InetSocketAddress gatewayAddress = this.server.getPeerByID(this.gatewayID);
        workers.remove(gatewayAddress);
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            this.logger = initializeLogging(RoundRobinLeader.class.getCanonicalName() + "-on-port-" + this.port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            //Thread.sleep(5000); // sleep 5 seconds to make sure the previous worker on port + 2 has time to close
            this.gatewayServerSocket = new ServerSocket(this.tcpPort);
            logger.info("ServerSocket created on port " + this.tcpPort + " on host " + this.server.getAddress().getHostString());
        } catch (IOException e) {
            logger.severe("Error creating ServerSocket on port " + this.tcpPort + " on host " + this.server.getAddress().getHostString());
            throw new RuntimeException(e);
        }

        setName("RoundRobinLeader-port-" + this.port);
        logger.info("RoundRobinLeader created");
    }

    @Override
    public void run() {
        logger.info("RoundRobinLeader started");
        while (!this.isInterrupted()) {
            Socket gatewaySocket;
            try {
                logger.info("Waiting for connection");
                gatewaySocket = this.gatewayServerSocket.accept();
                logger.info("Accepted connection");
            } catch (IOException e) {
                logger.severe("Error accepting connection");
                throw new RuntimeException(e);
            }
            InetSocketAddress nextWorker = chooseNextWorker();
            workerTask task = new workerTask(this.server, this.port, gatewaySocket, nextWorker, this.logger, this.requestIDtoAddress);
            this.executor.execute(task);
            logger.info("Worker task started");
        }
        logger.severe("RoundRobinLeader shutdown");
        this.executor.shutdown();
        try {
            this.gatewayServerSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized InetSocketAddress chooseNextWorker() {
        if(this.nextWorkerIndex >= this.workers.size()){
            this.nextWorkerIndex = 0;
        }
        InetSocketAddress nextWorker = this.workers.get(this.nextWorkerIndex);
        synchronized (deadServers){
            if(deadServers.contains(nextWorker)){
                this.workers.remove(nextWorker);
                this.nextWorkerIndex++;
                return chooseNextWorker();
            }
        }
        this.nextWorkerIndex++;
        return nextWorker;
    }
    private class workerTask extends Thread{
        private Socket gatewaySocket;
        private InetSocketAddress workerAddress;
        private Logger logger;
        //private long requestID;
        private ConcurrentHashMap<Long, InetSocketAddress> requestIDtoAddress;
        private PeerServerImpl server;
        private int port;
        private int workerTCPport;
        boolean error = false;
        boolean sentResult = false;

        private workerTask(PeerServerImpl peerServer, int port, Socket gatewaySocket, InetSocketAddress workerAddress, Logger logger, ConcurrentHashMap<Long, InetSocketAddress> requestIDtoAddress){
            this.gatewaySocket = gatewaySocket;
            this.workerAddress = workerAddress;
            this.workerTCPport = workerAddress.getPort() + 2;
            this.logger = logger;
            this.requestIDtoAddress = requestIDtoAddress;
            this.server = peerServer;
            this.port = port;
        }

        @Override
        public void run() {
            Message msg = null;
            Message resultMessage = null;
            InputStream is;
            OutputStream os;
            try {
                is = gatewaySocket.getInputStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                os = gatewaySocket.getOutputStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            byte[] msgArray;
            logger.info("Reading message from gateway");
            byte[] lengthArray = new byte[4];
            try {
                is.read(lengthArray);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            int length = bytesToInt(lengthArray);
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int bytesRead;
            int totalBytesRead = 0;
            try {
                while (totalBytesRead < length && (bytesRead = is.read(buffer, 0, buffer.length)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            msgArray = byteArrayOutputStream.toByteArray();
            logger.info("Message read");
            msg = new Message(msgArray);
            char msgType = msg.getMessageType().getChar();
            long requestID = msg.getRequestID();
            boolean errorOccurred = msg.getErrorOccurred();
            int senderPort = msg.getSenderPort();
            int senderHostLength = msg.getSenderHost().length();
            String senderHost = msg.getSenderHost();
            int receiverPort = msg.getReceiverPort();
            int receiverHostLength = msg.getReceiverHost().length();
            String receiverHost = msg.getReceiverHost();
            int contentLength = msg.getMessageContents().length;
            byte[] content = msg.getMessageContents();
            while(!sentResult){
                try{
                    if(msgType == Message.MessageType.WORK.getChar()){
                        logger.info("message type work, calling send message to next worker");
                        sendMessageToNextWorker(msg, this.workerAddress, is, os);
                        continue;
                    }
                    if(msgType == Message.MessageType.NEW_LEADER_GETTING_LAST_WORK.getChar()){
                        for(InetSocketAddress worker: workers){
                            if(sentResult){ // if the previous worker was the one who had this cached continue
                                continue;
                            }
                            logger.info("sending to next worker");
                            sendMessageToNextWorker(msg, worker, is, os);
                        }
                    }
                }catch (Exception e){
                    this.logger.severe("Error in workerTask, worker down");
                    error = true;
                }
            }
        }
        private void sendMessageToNextWorker(Message msg, InetSocketAddress nextWorker, InputStream is, OutputStream os) {
            try{
                Socket workerSocket;

                if(error){ //if error flag set, need the next worker because previous one failed
                    this.workerAddress = chooseNextWorker();
                }
                error = false;
                workerSocket = new Socket(nextWorker.getHostString(), nextWorker.getPort() + 2);
                InputStream workerIS;
                OutputStream workerOS;
                workerIS = workerSocket.getInputStream();
                workerOS = workerSocket.getOutputStream();
                this.logger.info("Sent work to worker " + nextWorker.getHostString() + ":" + nextWorker.getPort());
                workerOS.write(intToBytes(msg.getNetworkPayload().length));
                workerOS.write(msg.getNetworkPayload());
                workerOS.flush();
                byte[] responseArray;
                byte[] lengthArray = new byte[4];
                workerIS.read(lengthArray);
                int length = bytesToInt(lengthArray);
                byte[] buffer = new byte[1024];
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int bytesRead = 0;
                int totalBytesRead = 0;

                while (totalBytesRead < length && (bytesRead = workerIS.read(buffer, 0, Math.min(buffer.length, length - totalBytesRead))) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
                if(bytesRead == -1){
                    logger.info("Worker did not have it cached");
                    return;
                }
                responseArray = byteArrayOutputStream.toByteArray();
                Message resultMessage = new Message(responseArray);
                this.logger.info("Received response from worker " + nextWorker.getHostString() + ":" + nextWorker.getPort());
                workerIS.close();
                workerOS.close();
                workerSocket.close();
                if(resultMessage.getMessageType() == Message.MessageType.COMPLETED_WORK) {
                    this.logger.info("sent results is true");
                    os.write(intToBytes(resultMessage.getNetworkPayload().length));
                    os.write(resultMessage.getNetworkPayload());
                    os.flush();
                    sentResult = true;
                    is.close();
                    os.close();
                    gatewaySocket.close();
                }
            }catch (Exception e){
                this.logger.severe("Error in workerTask, worker down");
                error = true;
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
