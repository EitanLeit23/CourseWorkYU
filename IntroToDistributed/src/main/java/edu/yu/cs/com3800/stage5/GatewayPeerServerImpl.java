package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.PeerServer;
import edu.yu.cs.com3800.Vote;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

public class GatewayPeerServerImpl extends PeerServerImpl {
    public GatewayPeerServerImpl(int udpPort, long peerEpoch, Long serverID, Map<Long, InetSocketAddress> peerIDtoAddress, Long gatewayID, int numberOfObservers) throws IOException {
        super(udpPort, peerEpoch, serverID, peerIDtoAddress, gatewayID, numberOfObservers);
        this.setPeerState(ServerState.OBSERVER);
    }
    @Override
    public void setPeerState(ServerState state){
        super.setPeerState(ServerState.OBSERVER);
    }
    @Override
    public ServerState getPeerState(){
        return ServerState.OBSERVER;
    }
    /*public long getCurrentLeaderID(){
        try {
            PeerServerImpl.latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return this.getCurrentLeader().getProposedLeaderID();
    }*/
}
