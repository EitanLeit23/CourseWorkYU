package edu.yu.cs.com3800;

import edu.yu.cs.com3800.stage5.GatewayServer;
import edu.yu.cs.com3800.stage5.PeerServerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Stage5Test {
    private String validSrc;
    private String invalidSrc;
    private String importViolation;
    private String constructorViolation;
    private String IAESrc;
    @AfterEach
    public void waitForThreads() {
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
        }
    }

    @BeforeEach
    public void setUp() {
        this.validSrc = "public class SimpleClass {\n" +
                "    // Constructor that takes no arguments\n" +
                "    public SimpleClass() {\n" +
                "    }\n" +
                "\n" +
                "    // Public method named run, takes no arguments, and returns a String\n" +
                "    public String run() {\n" +
                "        return \"Hello, World!\";\n" +
                "    }\n" +
                "}";
        this.invalidSrc = "public class Main { public static void main(String[] args) { System.out.println(\"Hello, World!\"); } }";
        this.importViolation = "public class ImportViolation {\n" +
                "    import java.util.Scanner; // This import is not built into the JRE\n" +
                "\n" +
                "    public ImportViolation() {\n" +
                "    }\n" +
                "\n" +
                "    public String run() {\n" +
                "        return \"Hello, World!\";\n" +
                "    }\n" +
                "}";
        this.constructorViolation = "public class ConstructorViolation {\n" +
                "    // This class does not have a constructor that takes no arguments\n" +
                "    public ConstructorViolation(int arg) {\n" +
                "    }\n" +
                "\n" +
                "    public String run() {\n" +
                "        return \"Hello, World!\";\n" +
                "    }\n" +
                "}";
        this.IAESrc = "public class SimpleClass {\n" +
                "    // Constructor that takes no arguments\n" +
                "    public SimpleClass() {\n" +
                "    }\n" +
                "\n" +
                "    // Public method named run, takes no arguments, and returns a String\n" +
                "    public String run() {\n" +
                "        throw new IllegalArgumentException(\"This is an illegal argumon\");\n" +
                "    }\n" +
                "}";
    }

    public void shutdownAll(ArrayList<PeerServer> servers){
        for(PeerServer p : servers){
            p.shutdown();
        }
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
        }
    }

    @Test
    public void simpleTest() throws IOException, InterruptedException {
        LinkedBlockingQueue<Message> outgoingMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<>();
        UDPMessageReceiver receiver = new UDPMessageReceiver(incomingMessages, new InetSocketAddress("localhost", 9999), 9999, null);
        UDPMessageSender sender = new UDPMessageSender(outgoingMessages, 9999);

        //create IDs and addresses
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

        //create servers
        ArrayList<PeerServer> servers = new ArrayList<>(3);
        int i = 0;
        GatewayServer gatewayServer = null;
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            if (i == peerIDtoAddress.size() - 1) {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                //map.remove(entry.getKey());
                gatewayServer = new GatewayServer(8888, 8090, 0, entry.getKey(), new ConcurrentHashMap<>(map), 1);
                new Thread(gatewayServer, "Server on port " + 8090).start();
            } else {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                map.remove(entry.getKey());
                PeerServerImpl server = new PeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, 9L, 1);
                servers.add(server);
                new Thread(server, "Server on port " + server.getAddress().getPort()).start();
            }
            i++;
        }
        //wait for threads to start
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
        }
        //print out the leaders and shutdown
        for (PeerServer server : servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null) {
                System.out.println("Server on port " + server.getAddress().getPort() + " whose ID is " + server.getServerId() + " has the following ID as its leader: " + leader.getProposedLeaderID() + " and its state is " + server.getPeerState().name());

            }
        }
        Vote leader = servers.get(0).getCurrentLeader();
        InetSocketAddress leaderAddress = peerIDtoAddress.get(leader.getProposedLeaderID());
        System.out.println("Leader is on port " + leaderAddress.getPort());
        System.out.println("Leader is on port " + peerIDtoAddress.get(gatewayServer.getGatewayPeerServer().getCurrentLeader().getProposedLeaderID()));
        URL url = new URL("http", "localhost", 8888, "/compileandrun");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.validSrc))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.validSrc))
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.invalidSrc))
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.invalidSrc))
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.importViolation))
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.importViolation))
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.constructorViolation))
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.constructorViolation))
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.IAESrc))
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(this.IAESrc))
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        System.out.println(response.statusCode());
        System.out.println(response.headers());
        shutdownAll(servers);
    }
    @Test
    public void manyClientsTest() throws IOException, InterruptedException {
        LinkedBlockingQueue<Message> outgoingMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<>();
        UDPMessageReceiver receiver = new UDPMessageReceiver(incomingMessages, new InetSocketAddress("localhost", 9899), 9899, null);
        UDPMessageSender sender = new UDPMessageSender(outgoingMessages, 9899);

        //create IDs and addresses
        HashMap<Long, InetSocketAddress> peerIDtoAddress = new HashMap<>();
        peerIDtoAddress.put(1L, new InetSocketAddress("localhost", 8110));
        peerIDtoAddress.put(2L, new InetSocketAddress("localhost", 8120));
        peerIDtoAddress.put(3L, new InetSocketAddress("localhost", 8130));
        peerIDtoAddress.put(4L, new InetSocketAddress("localhost", 8140));
        peerIDtoAddress.put(5L, new InetSocketAddress("localhost", 8150));
        peerIDtoAddress.put(6L, new InetSocketAddress("localhost", 8160));
        peerIDtoAddress.put(7L, new InetSocketAddress("localhost", 8170));
        peerIDtoAddress.put(8L, new InetSocketAddress("localhost", 8180));
        peerIDtoAddress.put(9L, new InetSocketAddress("localhost", 8190));

        //create servers
        ArrayList<PeerServer> servers = new ArrayList<>(3);
        int i = 0;
        GatewayServer gatewayServer = null;
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            if (i == peerIDtoAddress.size() - 1) {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                //map.remove(entry.getKey());
                gatewayServer = new GatewayServer(8788, 8190, 0, entry.getKey(), new ConcurrentHashMap<>(map), 1);
                new Thread(gatewayServer, "Server on port " + 8190).start();
            } else {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                map.remove(entry.getKey());
                PeerServerImpl server = new PeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, 9L, 1);
                servers.add(server);
                new Thread(server, "Server on port " + server.getAddress().getPort()).start();
            }
            i++;
        }
        //wait for threads to start
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
        }
        //print out the leaders and shutdown
        for (PeerServer server : servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null) {
                System.out.println("Server on port " + server.getAddress().getPort() + " whose ID is " + server.getServerId() + " has the following ID as its leader: " + leader.getProposedLeaderID() + " and its state is " + server.getPeerState().name());

            }
        }

        Vote leader = servers.get(0).getCurrentLeader();
        InetSocketAddress leaderAddress = peerIDtoAddress.get(leader.getProposedLeaderID());
        System.out.println("Leader is on port " + leaderAddress.getPort());
        System.out.println("Leader is on port " + peerIDtoAddress.get(gatewayServer.getGatewayPeerServer().getCurrentLeader().getProposedLeaderID()));
        int threadNum = 20;
        CountDownLatch latch = new CountDownLatch(threadNum);
        for(int j = 0; j < threadNum; j++) {
            String message = this.validSrc.replace("World!", "World! from code version " + j);
            new HttpClientThread(8788, message, j, latch).start();
        }
        latch.await();
        shutdownAll(servers);
    }
    @Test
    public void gossipTest() throws IOException {
        LinkedBlockingQueue<Message> outgoingMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<>();
        UDPMessageReceiver receiver = new UDPMessageReceiver(incomingMessages, new InetSocketAddress("localhost", 9799), 9799, null);
        UDPMessageSender sender = new UDPMessageSender(outgoingMessages, 9799);

        //create IDs and addresses
        HashMap<Long, InetSocketAddress> peerIDtoAddress = new HashMap<>();
        peerIDtoAddress.put(1L, new InetSocketAddress("localhost", 8210));
        peerIDtoAddress.put(2L, new InetSocketAddress("localhost", 8220));
        peerIDtoAddress.put(3L, new InetSocketAddress("localhost", 8230));
        peerIDtoAddress.put(4L, new InetSocketAddress("localhost", 8240));
        peerIDtoAddress.put(5L, new InetSocketAddress("localhost", 8250));
        peerIDtoAddress.put(6L, new InetSocketAddress("localhost", 8260));
        peerIDtoAddress.put(7L, new InetSocketAddress("localhost", 8270));
        peerIDtoAddress.put(8L, new InetSocketAddress("localhost", 8280));
        peerIDtoAddress.put(9L, new InetSocketAddress("localhost", 8290));

        //create servers
        ArrayList<PeerServer> servers = new ArrayList<>(3);
        int i = 0;
        GatewayServer gatewayServer = null;
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            if (i == peerIDtoAddress.size() - 1) {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                //map.remove(entry.getKey());
                gatewayServer = new GatewayServer(8688, 8290, 0, entry.getKey(), new ConcurrentHashMap<>(map), 1);
                new Thread(gatewayServer, "Server on port " + 8290).start();
            } else {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                map.remove(entry.getKey());
                PeerServerImpl server = new PeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, 9L, 1);
                servers.add(server);
                new Thread(server, "Server on port " + server.getAddress().getPort()).start();
            }
            i++;
        }
        //wait for threads to start
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
        }
        //print out the leaders and shutdown
        for (PeerServer server : servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null) {
                System.out.println("Server on port " + server.getAddress().getPort() + " whose ID is " + server.getServerId() + " has the following ID as its leader: " + leader.getProposedLeaderID() + " and its state is " + server.getPeerState().name());

            }
        }

        Vote leader = servers.get(0).getCurrentLeader();
        InetSocketAddress leaderAddress = peerIDtoAddress.get(leader.getProposedLeaderID());
        System.out.println("Leader is on port " + leaderAddress.getPort());
        System.out.println("Leader is on port " + peerIDtoAddress.get(gatewayServer.getGatewayPeerServer().getCurrentLeader().getProposedLeaderID()));
        System.out.println("now watching gossip for 30 seconds");
        try {
            Thread.sleep(30000);
        } catch (Exception e) {
        }
        System.out.println("shutting down worker server");
        servers.get(0).shutdown();
        System.out.println("now watching gossip for 30 seconds");
        try {
            Thread.sleep(30000);
        } catch (Exception e) {
        }
        shutdownAll(servers);        
    }
    @Test
    public void workerFail() throws IOException, InterruptedException {
        LinkedBlockingQueue<Message> outgoingMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<>();
        UDPMessageReceiver receiver = new UDPMessageReceiver(incomingMessages, new InetSocketAddress("localhost", 9699), 9699, null);
        UDPMessageSender sender = new UDPMessageSender(outgoingMessages, 9699);

        //create IDs and addresses
        HashMap<Long, InetSocketAddress> peerIDtoAddress = new HashMap<>();
        peerIDtoAddress.put(1L, new InetSocketAddress("localhost", 8310));
        peerIDtoAddress.put(2L, new InetSocketAddress("localhost", 8320));
        peerIDtoAddress.put(3L, new InetSocketAddress("localhost", 8330));
        peerIDtoAddress.put(4L, new InetSocketAddress("localhost", 8340));
        peerIDtoAddress.put(5L, new InetSocketAddress("localhost", 8350));
        peerIDtoAddress.put(6L, new InetSocketAddress("localhost", 8360));
        peerIDtoAddress.put(7L, new InetSocketAddress("localhost", 8370));
        peerIDtoAddress.put(8L, new InetSocketAddress("localhost", 8380));
        peerIDtoAddress.put(9L, new InetSocketAddress("localhost", 8390));

        //create servers
        ArrayList<PeerServer> servers = new ArrayList<>(3);
        int i = 0;
        GatewayServer gatewayServer = null;
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            if (i == peerIDtoAddress.size() - 1) {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                //map.remove(entry.getKey());
                gatewayServer = new GatewayServer(8588, 8390, 0, entry.getKey(), new ConcurrentHashMap<>(map), 1);
                new Thread(gatewayServer, "Server on port " + 8390).start();
            } else {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                map.remove(entry.getKey());
                PeerServerImpl server = new PeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, 9L, 1);
                servers.add(server);
                new Thread(server, "Server on port " + server.getAddress().getPort()).start();
            }
            i++;
        }
        //wait for threads to start
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
        }
        //print out the leaders and shutdown
        for (PeerServer server : servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null) {
                System.out.println("Server on port " + server.getAddress().getPort() + " whose ID is " + server.getServerId() + " has the following ID as its leader: " + leader.getProposedLeaderID() + " and its state is " + server.getPeerState().name());

            }
        }

        Vote leader = servers.get(0).getCurrentLeader();
        InetSocketAddress leaderAddress = peerIDtoAddress.get(leader.getProposedLeaderID());
        System.out.println("Leader is on port " + leaderAddress.getPort());
        System.out.println("Leader is on port " + peerIDtoAddress.get(gatewayServer.getGatewayPeerServer().getCurrentLeader().getProposedLeaderID()));
        System.out.println("sending 7 requests");
        int threadNum = 7;
        CountDownLatch latch = new CountDownLatch(threadNum);
        for(int j = 0; j < threadNum; j++) {
            String message = this.validSrc.replace("World!", "World! from code version " + j);
            new HttpClientThread(8588, message, j, latch).start();
        }
        latch.await();

        System.out.println("shutting down worker server");
        servers.get(0).shutdown();
        System.out.println("now watching gossip for 60 seconds");
        try {
            Thread.sleep(30000);
        } catch (Exception e) {
        }
        System.out.println("now sending out 7 more requests");
        latch = new CountDownLatch(threadNum);
        for(int j = 0; j < threadNum; j++) {
            String message = this.validSrc.replace("World!", "World! from code version " + j + ".2");
            new HttpClientThread(8588, message, j, latch).start();
        }
        latch.await();
        shutdownAll(servers);        
    }
    @Test
    public void LeaderFailure() throws InterruptedException, IOException {
        LinkedBlockingQueue<Message> outgoingMessages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<>();
        UDPMessageReceiver receiver = new UDPMessageReceiver(incomingMessages, new InetSocketAddress("localhost", 9599), 9599, null);
        UDPMessageSender sender = new UDPMessageSender(outgoingMessages, 9599);

        //create IDs and addresses
        HashMap<Long, InetSocketAddress> peerIDtoAddress = new HashMap<>();
        peerIDtoAddress.put(1L, new InetSocketAddress("localhost", 8410));
        peerIDtoAddress.put(2L, new InetSocketAddress("localhost", 8420));
        peerIDtoAddress.put(3L, new InetSocketAddress("localhost", 8430));
        peerIDtoAddress.put(4L, new InetSocketAddress("localhost", 8440));
        peerIDtoAddress.put(5L, new InetSocketAddress("localhost", 8450));
        peerIDtoAddress.put(6L, new InetSocketAddress("localhost", 8460));
        peerIDtoAddress.put(7L, new InetSocketAddress("localhost", 8470));
        peerIDtoAddress.put(8L, new InetSocketAddress("localhost", 8480));
        peerIDtoAddress.put(9L, new InetSocketAddress("localhost", 8490));

        //create servers
        ArrayList<PeerServer> servers = new ArrayList<>(3);
        int i = 0;
        GatewayServer gatewayServer = null;
        for (Map.Entry<Long, InetSocketAddress> entry : peerIDtoAddress.entrySet()) {
            if (i == peerIDtoAddress.size() - 1) {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                //map.remove(entry.getKey());
                gatewayServer = new GatewayServer(8488, 8490, 0, entry.getKey(), new ConcurrentHashMap<>(map), 1);
                new Thread(gatewayServer, "Server on port " + 8490).start();
            } else {
                HashMap<Long, InetSocketAddress> map = (HashMap<Long, InetSocketAddress>) peerIDtoAddress.clone();
                map.remove(entry.getKey());
                PeerServerImpl server = new PeerServerImpl(entry.getValue().getPort(), 0, entry.getKey(), map, 9L, 1);
                servers.add(server);
                new Thread(server, "Server on port " + server.getAddress().getPort()).start();
            }
            i++;
        }
        //wait for threads to start
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
        }
        //print out the leaders and shutdown
        for (PeerServer server : servers) {
            Vote leader = server.getCurrentLeader();
            if (leader != null) {
                System.out.println("Server on port " + server.getAddress().getPort() + " whose ID is " + server.getServerId() + " has the following ID as its leader: " + leader.getProposedLeaderID() + " and its state is " + server.getPeerState().name());

            }
        }

        Vote leader = servers.get(0).getCurrentLeader();
        InetSocketAddress leaderAddress = peerIDtoAddress.get(leader.getProposedLeaderID());
        System.out.println("Leader is on port " + leaderAddress.getPort());
        System.out.println("Leader is on port " + peerIDtoAddress.get(gatewayServer.getGatewayPeerServer().getCurrentLeader().getProposedLeaderID()));
        System.out.println("sending 7 requests");
        int threadNum = 7;
        CountDownLatch latch = new CountDownLatch(threadNum);
        for(int j = 0; j < threadNum; j++) {
            String message = this.validSrc.replace("World!", "World! from code version " + j);
            new HttpClientThread(8488, message, j, latch).start();
        }
        latch.await();

        System.out.println("shutting down leader server");
        for(PeerServer server : servers) {
            if(server.getServerId() == leader.getProposedLeaderID()) {
                System.out.println("shutting down leader");
                server.shutdown();
            }
        }
        System.out.println("now watching gossip for 30 seconds");
        try {
            Thread.sleep(30000);
        } catch (Exception e) {
        }
        System.out.println("now sending out 7 more requests");
        latch = new CountDownLatch(threadNum);
        for(int j = 0; j < threadNum; j++) {
            String message = this.validSrc.replace("World!", "World! from code version " + j + ".2");
            new HttpClientThread(8488, message, j, latch).start();
        }
        latch.await();
        shutdownAll(servers);        
    }

    private class HttpClientThread extends Thread {
        private final int httpPort;
        private final String src;
        private final int id;
        private final CountDownLatch latch;

        public HttpClientThread(int httpPort, String src, int id, CountDownLatch latch) {
            this.httpPort = httpPort;
            this.src = src;
            this.id = id;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                URL url = new URL("http", "localhost", this.httpPort, "/compileandrun");
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url.toString()))
                        .header("Content-Type", "text/x-java-source")
                        .POST(HttpRequest.BodyPublishers.ofString(this.src))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response.headers());
                System.out.println("Thread " + Thread.currentThread().getId() + " - Response Body: " + response.body() + "\n" + "Thread " + Thread.currentThread().getId() + " - Response is 200 OK: " + (response.statusCode() == 200));
                this.latch.countDown();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                this.latch.countDown();
            }
        }
    }
}