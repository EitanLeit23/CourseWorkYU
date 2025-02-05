package edu.yu.cs.com3800.stage5;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ServerMakerForDemo {
    public static void main(String[] args) throws IOException {
        HashMap<Long, InetSocketAddress> peerIDtoAddress = new HashMap<>();
        peerIDtoAddress.put(1L, new InetSocketAddress("localhost", 8010));
        peerIDtoAddress.put(2L, new InetSocketAddress("localhost", 8020));
        peerIDtoAddress.put(3L, new InetSocketAddress("localhost", 8030));
        peerIDtoAddress.put(4L, new InetSocketAddress("localhost", 8040));
        peerIDtoAddress.put(5L, new InetSocketAddress("localhost", 8050));
        peerIDtoAddress.put(6L, new InetSocketAddress("localhost", 8060));
        peerIDtoAddress.put(7L, new InetSocketAddress("localhost", 8070));
        peerIDtoAddress.put(8L, new InetSocketAddress("localhost", 8080));
        peerIDtoAddress.put(9L, new InetSocketAddress("localhost", 8090));

        long myId = Long.parseLong(args[0]);
        int udpPort = Integer.parseInt(args[1]);
        long gatewayId = 9L;


        if(myId == gatewayId){
            HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
            GatewayServer gateway = new GatewayServer(8888, udpPort, 0, myId, new ConcurrentHashMap<>(map), 1);
            gateway.start();
        }
        else{
            HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
            map.remove(myId);
            PeerServerImpl server = new PeerServerImpl(udpPort, 0, myId, map, gatewayId, 1);
            server.start();
        }

    }
}
