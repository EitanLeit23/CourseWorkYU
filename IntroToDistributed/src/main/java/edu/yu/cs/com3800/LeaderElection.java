package edu.yu.cs.com3800;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**We are implemeting a simplfied version of the election algorithm. For the complete version which covers all possible scenarios, see https://github.com/apache/zookeeper/blob/90f8d835e065ea12dddd8ed9ca20872a4412c78a/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/FastLeaderElection.java#L913
 */
public class LeaderElection {
    /**
     * time to wait once we believe we've reached the end of leader election.
     */
    private final static int finalizeWait = 3200;

    /**
     * Upper bound on the amount of time between two consecutive notification checks.
     * This impacts the amount of time to get the system up again after long partitions. Currently 30 seconds.
     */
    private final static int maxNotificationInterval = 30000;
    private final PeerServer server;
    private final LinkedBlockingQueue<Message> incomingMessages;
    private final Logger logger;
    private long proposedLeader;
    private long proposedEpoch;
    private int initialWait = 500;
    private long originalLeaderID;
    //private int waitCounter = 0;
    private HashMap<Long, ElectionNotification> votes;
    public LeaderElection(PeerServer server, LinkedBlockingQueue<Message> incomingMessages, Logger logger) {
        if (server == null || incomingMessages == null || logger == null) {
            throw new IllegalArgumentException();
        }
        this.logger = logger;
        this.server = server;
        this.incomingMessages = incomingMessages;
        Vote currentLeader = server.getCurrentLeader();
        this.proposedLeader = currentLeader.getProposedLeaderID();
        this.originalLeaderID = currentLeader.getProposedLeaderID();
        this.proposedEpoch = currentLeader.getPeerEpoch();
        this.votes = new HashMap<>();
    }

    /**
     * Note that the logic in the comments below does NOT cover every last "technical" detail you will need to address to implement the election algorithm.
     * How you store all the relevant state, etc., are details you will need to work out.
     * @return the elected leader
     */
    public synchronized Vote lookForLeader() {
        try {
            logger.info("Starting election");
            //send initial notifications to get things started
            sendNotifications();
            Thread.sleep(this.initialWait);
            //Loop in which we exchange notifications with other servers until we find a leader
            while(true){
                //Remove next notification from queue
                Message incomingMsg = this.incomingMessages.poll(this.initialWait, TimeUnit.MILLISECONDS);
                //If no notifications received...
                    if(incomingMsg == null || incomingMsg.getMessageType() != Message.MessageType.ELECTION){
                        //...resend notifications to prompt a reply from others
                        sendNotifications();
                        //...use exponential back-off when notifications not received but no longer than maxNotificationInterval...
                        //waitCounter++;
                        this.initialWait = Math.min(2 * this.initialWait, maxNotificationInterval);
                }else{
                    //If we did get a message...
                    Message.MessageType type = incomingMsg.getMessageType();
                    ElectionNotification electionNotification = getNotificationFromMessage(incomingMsg);
                    long leader = electionNotification.getProposedLeaderID();
                    char stateChar = electionNotification.getState().getChar();
                    long senderID = electionNotification.getSenderID();
                    long peerEpoch = electionNotification.getPeerEpoch();
                    //...if it's for an earlier epoch, or from an observer, ignore it.
                    if(peerEpoch < this.proposedEpoch || stateChar == 'B'){
                        this.logger.info("Ignoring message from server " + senderID + " with epoch " + peerEpoch);
                        continue;
                    }
                    //...if the received message has a vote for a leader which supersedes mine, change my vote (and send notifications to all other voters about my new vote).
                    if(supersedesCurrentVote(leader, peerEpoch)){
                        this.proposedLeader = leader;
                        this.proposedEpoch = peerEpoch;
                        sendNotifications();
                        this.votes.put(this.server.getServerId(), electionNotification);
                    }
                    //(Be sure to keep track of the votes I received and who I received them from.)
                    this.votes.put(senderID, electionNotification);
                    //If I have enough votes to declare my currently proposed leader as the leader...
                    if(!this.haveEnoughVotes(this.votes, new Vote(this.proposedLeader, this.proposedEpoch))){
                       continue;
                    }

                    //..do a last check to see if there are any new votes for a higher ranked possible leader. If there are, continue in my election "while" loop.
                    if(this.incomingMessages.peek() != null){
                        continue;
                    }
                    Thread.sleep(finalizeWait);
                    if(this.incomingMessages.peek() != null){
                        continue;
                    }
                    logger.info("Election completed; leader is " + this.proposedLeader);
                    //If there are no new relevant message from the reception queue, set my own state to either LEADING or FOLLOWING and RETURN the elected leader.
                    return acceptElectionWinner(new ElectionNotification(this.proposedLeader, this.server.getPeerState(), this.server.getServerId(), this.proposedEpoch));
                }
            }
        }
        catch (Exception e) {
            this.logger.log(Level.SEVERE,"Exception occurred during election; election canceled",e);
        }
        return null;
    }
    private void sendNotifications() {
        //send a notification to all other servers
        ElectionNotification n = new ElectionNotification(this.proposedLeader, this.server.getPeerState(), this.server.getServerId(), this.proposedEpoch);
        this.server.sendBroadcast(Message.MessageType.ELECTION, buildMsgContent(n));
    }
    public static byte[] buildMsgContent(ElectionNotification n) {
        //convert the ElectionNotification object to a byte array
        long leader = n.getProposedLeaderID();
        char stateChar = n.getState().getChar();
        long senderID = n.getSenderID();
        long peerEpoch = n.getPeerEpoch();
        ByteBuffer msgBytes = ByteBuffer.wrap(new byte[26]); // 26 = 8 + 2 + 8 + 8
        msgBytes.putLong(leader);
        msgBytes.putChar(stateChar);
        msgBytes.putLong(senderID);
        msgBytes.putLong(peerEpoch);
        return msgBytes.array();
    }
    public static ElectionNotification getNotificationFromMessage(Message m) {
        //convert a byte array to an ElectionNotification object
        ByteBuffer msgBytes = ByteBuffer.wrap(m.getMessageContents());
        long leader = msgBytes.getLong();
        char stateChar = msgBytes.getChar();
        long senderID = msgBytes.getLong();
        long peerEpoch = msgBytes.getLong();
        return new ElectionNotification(leader, PeerServer.ServerState.getServerState(stateChar), senderID, peerEpoch);
    }

    private Vote acceptElectionWinner(ElectionNotification n) {
        //set my state to either LEADING or FOLLOWING
        if(n.getProposedLeaderID() == this.server.getServerId()) {
            this.server.setPeerState(PeerServer.ServerState.LEADING);
            logger.info("Server on port " + this.server.getUdpPort() + " whose ID is " + this.server.getServerId() + " is now the leader");
        }else if(this.server.getPeerState() != PeerServer.ServerState.OBSERVER){
            this.server.setPeerState(PeerServer.ServerState.FOLLOWING);
            logger.info("Server on port " + this.server.getUdpPort() + " whose ID is " + this.server.getServerId() + " is now a follower");
        }if (this.server.getPeerState() == PeerServer.ServerState.OBSERVER){
            logger.info("Server on port " + this.server.getUdpPort() + " whose ID is " + this.server.getServerId() + " is now an observer");
        }
        //clear out the incoming queue before returning
        this.incomingMessages.clear();
        return new Vote(n.getProposedLeaderID(), n.getPeerEpoch());
    }

    /*
     * We return true if one of the following two cases hold:
     * 1- New epoch is higher
     * 2- New epoch is the same as current epoch, but server id is higher.
     */
    protected boolean supersedesCurrentVote(long newId, long newEpoch) {
        if(this.server.getPeerState() == PeerServer.ServerState.OBSERVER && this.proposedLeader == this.originalLeaderID){
            return true;
        }
        return (newEpoch > this.proposedEpoch) || ((newEpoch == this.proposedEpoch) && (newId > this.proposedLeader));
    }

    /**
     * Termination predicate. Given a set of votes, determines if we have sufficient support for the proposal to declare the end of the election round.
     * Who voted for who isn't relevant, we only care that each server has one current vote.
     */
    protected boolean haveEnoughVotes(Map<Long, ElectionNotification> votes, Vote proposal) {
        //is the number of votes for the proposal > the size of my peer server’s quorum?
        boolean won = false;
        int voteCount = 1;
        for(ElectionNotification n : votes.values()){
            if(proposal.equals(n)){
                voteCount++;
            }
        }
        if(voteCount >= this.server.getQuorumSize()){
            won = true;
        }
        return won;
    }
}