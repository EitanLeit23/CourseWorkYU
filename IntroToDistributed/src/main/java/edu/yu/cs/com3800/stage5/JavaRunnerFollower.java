package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class JavaRunnerFollower extends Thread implements LoggingServer {
    private PeerServerImpl server;
    //private LinkedBlockingQueue<Message> incomingMessages;
    //private LinkedBlockingQueue<Message> outgoingMessages;
    private Logger logger;
    private int port;
    private int tcpPort;
    private JavaRunner runner;
    private ServerSocket serverSocket;
    private List<InetSocketAddress> deadServers;

    public JavaRunnerFollower(PeerServerImpl server, int port, List<InetSocketAddress> deadServers) {
        try {
            this.logger = initializeLogging(JavaRunnerFollower.class.getCanonicalName() + "-on-port-" + port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.server = server;
        this.deadServers = deadServers;
        this.port = port;
        this.tcpPort = port + 2;
        setName("JavaRunnerFollower-port-" + this.port);
        logger.info("JavaRunnerFollower created");
        try {
            this.serverSocket = new ServerSocket(this.tcpPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            this.runner = new JavaRunner();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (!this.isInterrupted()) {
            Message msg = null;
            Message resultMessage = null;
            try{
                Socket senderSocket = null;
                senderSocket = this.serverSocket.accept();
                InputStream is;
                OutputStream os;
                is = senderSocket.getInputStream();
                os = senderSocket.getOutputStream();
                byte[] msgArray;
                logger.info("Reading message");
                byte[] lengthBytes = new byte[4];
                is.read(lengthBytes);
                int length = bytesToInt(lengthBytes);

                byte[] buffer = new byte[1024];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int bytesRead;
                int totalBytesRead = 0;
                while (totalBytesRead < length && (bytesRead = is.read(buffer, 0, Math.min(buffer.length, length - totalBytesRead))) != -1) {
                    baos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
                msgArray = baos.toByteArray();
                //msgArray = is.readAllBytes();
                logger.info("Message read");
                msg = new Message(msgArray);
                //Message msg = this.incomingMessages.poll();
                //if (msg == null) {
                //    continue;
                //}
                //byte[] payload = msg.getNetworkPayload();
                //ByteBuffer buffer = ByteBuffer.wrap(payload);
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
                //InputStream inputStream = new ByteArrayInputStream(content);
                logger.info("Message received");
                if(msgType == Message.MessageType.NEW_LEADER_GETTING_LAST_WORK.getChar()){
                    if(this.server.workerCache.containsKey(requestID)){
                        logger.info("recieved request for last work");
                        Message lastWork = this.server.workerCache.get(requestID);
                        if(lastWork == null){
                            logger.info("No last work");
                            is.close();
                            os.close();
                            senderSocket.close();
                            continue;
                        }
                        if(lastWork.getMessageType().equals(Message.MessageType.COMPLETED_WORK)){
                            logger.info("Sending last completed work");
                            os.write(intToBytes(lastWork.getNetworkPayload().length));
                            os.write(lastWork.getNetworkPayload());
                            os.flush();
                            is.close();
                            os.close();
                            senderSocket.close();
                            this.server.workerCache.remove(requestID);
                            continue;
                        }
                        if(lastWork.getMessageType().equals(Message.MessageType.WORK)){
                            logger.info("not completed work, doing work");
                            resultMessage = doWork(lastWork);
                            os.write(intToBytes(resultMessage.getNetworkPayload().length));
                            os.write(resultMessage.getNetworkPayload());
                            os.flush();
                            is.close();
                            os.close();
                            senderSocket.close();
                            this.server.workerCache.remove(requestID);
                            continue;
                        }

                    }
                }
                resultMessage = doWork(msg);
                //this.outgoingMessages.offer(resultMessage);
                logger.info("Sending result");
                os.write(intToBytes(resultMessage.getNetworkPayload().length));
                os.write(resultMessage.getNetworkPayload());
                os.flush();
                logger.info("Result sent");
                is.close();
                os.close();
                senderSocket.close();
            }catch(IOException e){
                logger.severe("Error with Leader connection");
                if (resultMessage != null) {
                    this.server.workerCache.put(resultMessage.getRequestID(), resultMessage);
                    continue;
                }
                if(msg != null && resultMessage == null){
                    this.server.workerCache.put(msg.getRequestID(), msg);
                }
            }
        }
        logger.severe("JavaRunnerFollower shutdown");
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public Message doWork(Message msg){
        InputStream inputStream = new ByteArrayInputStream(msg.getMessageContents());
        String result;
        Boolean err;
        Message toReturn;
        try {
            logger.info("Compiling and running");
            result = runner.compileAndRun(inputStream);
            //logger.info("Compiled and ran");
            err = false;
            //logger.info("Compiled and ran");
        } catch (Exception e) {
            logger.severe("Error compiling and running");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            e.printStackTrace(ps);
            String stackTrace = baos.toString();
            result= e.getMessage() + "\n" + stackTrace;
            err = true;
        }
        //new array for result message
        byte[] resultBytes = result.getBytes();
        toReturn = new Message(Message.MessageType.COMPLETED_WORK, resultBytes, this.server.getAddress().getHostString(), this.tcpPort, msg.getSenderHost(), msg.getSenderPort(), msg.getRequestID(), err);
        return toReturn;
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
