package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;


public class PeerServerImpl extends Thread implements PeerServer, LoggingServer {
    volatile boolean made = false;
    private final InetSocketAddress myAddress;
    private final int udpPort;
    private ServerState state;
    private volatile boolean shutdown;
    private LinkedBlockingQueue<Message> outgoingMessages;
    private LinkedBlockingQueue<Message> incomingMessages;
    private Long serverID;
    long peerEpoch;
    private volatile Vote currentLeader;
    public Map<Long,InetSocketAddress> peerIDtoAddress;
    private GossipThread gossipThread;

    ReentrantLock leaderLock = new ReentrantLock();

    public ConcurrentHashMap<Long, Message> workerCache;
    public ConcurrentHashMap<Long, Message> leaderCache;

    private UDPMessageSender senderWorker;
    private UDPMessageReceiver receiverWorker;
    private LeaderElection election;
    private Logger logger;
    private JavaRunnerFollower follower = null;
    private RoundRobinLeader leader = null;
    private Long gatewayID;
    private int numberOfObservers;
    private List<InetSocketAddress> deadServers;
    //private List<InetSocketAddress> liveServers;
    //protected static Phaser phaser = new Phaser(1);

    public PeerServerImpl(int udpPort, long peerEpoch, Long serverID, Map<Long, InetSocketAddress> peerIDtoAddress, Long gatewayID, int numberOfObservers) throws IOException{
        if(udpPort < 0 || peerEpoch < 0 || serverID < 0 || peerIDtoAddress == null || gatewayID < 0 || numberOfObservers < 0){
            throw new IllegalArgumentException();
        }
        this.udpPort = udpPort;
        this.myAddress = new InetSocketAddress("localhost", this.udpPort);
        this.peerEpoch = peerEpoch;
        this.serverID = serverID;
        this.peerIDtoAddress = new ConcurrentHashMap<>(peerIDtoAddress);
        this.peerIDtoAddress.remove(this.serverID);
        this.state = ServerState.LOOKING;
        this.outgoingMessages = new LinkedBlockingQueue<>();
        this.incomingMessages = new LinkedBlockingQueue<>();
        setName("PeerServerImpl-port-" + this.udpPort);
        this.logger = initializeLogging(PeerServerImpl.class.getCanonicalName() + "-on-port-" + this.udpPort);
        this.currentLeader = new Vote(this.serverID, this.peerEpoch);
        this.gatewayID = gatewayID;
        this.numberOfObservers = numberOfObservers;
        this.deadServers = new ArrayList<>();
        this.workerCache = new ConcurrentHashMap<>();
        this.leaderCache = new ConcurrentHashMap<>();
        /*this.liveServers = new ArrayList<>();
        for(InetSocketAddress peer : this.peerIDtoAddress.values()){
            if (peer.equals(this.myAddress)){
                continue;
            }
            this.liveServers.add(peer);
        }*/
    }

    @Override
    public void shutdown(){
        this.shutdown = true;
        logger.severe("Shutting down server on port " + this.udpPort);
        this.senderWorker.shutdown();
        this.receiverWorker.shutdown();
        this.gossipThread.shutdown();
        if(this.follower != null){
            logger.severe("Follower is not null, interrupting");
            this.follower.interrupt();
        }
        if(this.leader != null){
            logger.severe("Leader is not null, interrupting");
            this.leader.interrupt();
        }
    }

    @Override
    public void setCurrentLeader(Vote v) throws IOException {
        this.currentLeader = v;
    }

    @Override
    public Vote getCurrentLeader() {
        return this.currentLeader;
    }

    @Override
    public void sendMessage(Message.MessageType type, byte[] messageContents, InetSocketAddress target) throws IllegalArgumentException {
        if(type == null || messageContents == null || target == null){
            throw new IllegalArgumentException();
        }
        Message message = new Message(type, messageContents, this.myAddress.getHostString(), this.udpPort, target.getHostString(), target.getPort());
        this.outgoingMessages.offer(message);
    }

    @Override
    public void sendBroadcast(Message.MessageType type, byte[] messageContents) {
        if(type == null || messageContents == null){
            throw new IllegalArgumentException();
        }
        synchronized (this.deadServers){
            for(InetSocketAddress peer : this.peerIDtoAddress.values()){
                if (peer.equals(this.myAddress)){
                    continue;
                }
                if(this.deadServers.contains(peer)){
                    continue;
                }
                Message message = new Message(type, messageContents, this.myAddress.getHostString(), this.udpPort, peer.getHostString(), peer.getPort());
                this.outgoingMessages.offer(message);
            }
        }
    }

    @Override
    public ServerState getPeerState() {
        return this.state;
    }

    @Override
    public void setPeerState(ServerState newState) {
        this.state = newState;
    }

    @Override
    public Long getServerId() {
        return this.serverID;
    }

    @Override
    public long getPeerEpoch() {
        return this.peerEpoch;
    }

    @Override
    public InetSocketAddress getAddress() {
        return this.myAddress;
    }

    @Override
    public int getUdpPort() {
        return this.udpPort;
    }

    @Override
    public InetSocketAddress getPeerByID(long peerId) {
        return this.peerIDtoAddress.get(peerId);
    }

    @Override
    public int getQuorumSize() {
        int quorumSize;
        synchronized (deadServers){
            int liveServers = 0;
            for(InetSocketAddress peer : this.peerIDtoAddress.values()){
                if (peer.equals(this.myAddress)){
                    continue;
                }
                if(!this.deadServers.contains(peer)){
                    liveServers++;
                }
            }
            quorumSize = (liveServers - this.numberOfObservers + 1)/2 + 1;
        }
        return quorumSize;
    }

    @Override
    public void run(){
        try{
            //step 1: create and run thread that sends broadcast messages
            this.senderWorker = new UDPMessageSender(this.outgoingMessages,this.udpPort);
            this.senderWorker.setDaemon(true);
            this.senderWorker.start();

            //step 2: create and run thread that listens for messages sent to this server
            this.receiverWorker = new UDPMessageReceiver(this.incomingMessages,this.myAddress,this.udpPort,this);
            this.receiverWorker.setDaemon(true);
            this.receiverWorker.start();
            this.gossipThread = new GossipThread(this.incomingMessages, this.outgoingMessages, this.peerIDtoAddress, this.serverID, this, this.deadServers);
            this.gossipThread.setDaemon(true);
            this.gossipThread.start();
        }catch(IOException e){
            e.printStackTrace();
            return;
        }
        //step 3: main server loop
        try{
            while (!this.shutdown){
                switch (getPeerState()){
                    case OBSERVER:
                        if(!made){
                            this.leaderLock.lock();
                            this.currentLeader = new Vote(this.serverID, this.peerEpoch);
                            made = true;
                            this.logger.info("Server on port " + this.udpPort + " whose ID is " + this.getServerId() + " called leader election");
                            this.election = new LeaderElection(this, this.incomingMessages, this.logger);
                            this.logger.info("Server on port " + this.udpPort + " whose ID is " + this.getServerId() + " created election object");
                            Vote winner = this.election.lookForLeader();
                            this.currentLeader = winner;
                            this.leaderLock.unlock();
                        }
                        break;
                    case LOOKING:
                        if(this.follower != null){
                            this.follower.interrupt();
                            this.follower = null;
                        }
                        if(this.leader != null){
                            this.leader.interrupt();
                            this.leader = null;
                        }
                        this.leaderLock.lock();
                        this.currentLeader = new Vote(this.serverID, this.peerEpoch);
                        //start leader election, set leader to the election winner
                        this.logger.info("Server on port " + this.udpPort + " whose ID is " + this.getServerId() + " called leader election");
                        this.election = new LeaderElection(this, this.incomingMessages, this.logger);
                        Vote winner = this.election.lookForLeader();
                        this.currentLeader = winner;
                        this.leaderLock.unlock();
                        break;
                    case FOLLOWING:
                        if(this.leader != null){
                            this.leader.interrupt();
                            this.leader = null;
                        }
                        if(this.follower != null){
                            break;
                        }
                        this.follower = new JavaRunnerFollower(this, this.udpPort, this.deadServers);
                        this.logger.info("Server on port " + this.udpPort + " whose ID is " + this.getServerId() + " started worker thread");
                        this.follower.setDaemon(true);
                        this.follower.start();
                        break;
                    case LEADING:
                        if (this.follower != null){
                            logger.info("Follower is not null, interrupting");
                            this.follower.interrupt();
                            this.follower = null;
                        }
                        if(this.leader != null){
                            break;
                        }
                        this.logger.info("Server on port " + this.udpPort + " whose ID is " + this.getServerId() + " is starting master thread");
                        //udp port +2 ?
                        this.leader = new RoundRobinLeader(this, this.udpPort, new ArrayList<>(this.peerIDtoAddress.values()), this.deadServers, this.gatewayID);
                        this.logger.info("Server on port " + this.udpPort + " whose ID is " + this.getServerId() + "started master thread");
                        this.leader.setDaemon(true);
                        this.leader.start();
                        break;
                }
            }
        }
        catch (Exception e) {
           //code...
        }
    }

}
