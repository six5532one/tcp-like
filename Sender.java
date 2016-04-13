import java.util.HashMap;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.File;

class Sender    {
    // before computing timeout interval dynamically, initialize to 5s
    public static final int INITIAL_TIMEOUT_INT = 5000;
    private int nextSeqNum = 0;
    private int numDupAcks = 0;
    private HashMap<Integer, Long> inTransitSendTimes;
    private HashMap<Integer, DatagramPacket> inTransitPackets;
    private int estimatedRTT = 0;
    private int devRTT = 0;
    // provide a limited form of congestion control
    private int retransmissionTimeoutInt = 0;
    private int windowSize;
    private InetAddress destIP;
    private int destPort, ackPort;

    public Sender(String remoteIPStr, int remotePort, int ackPort, int windowSize) {
        this.windowSize = windowSize;
        inTransitSendTimes = new HashMap<Integer, Long>();
        inTransitPackets = new HashMap<Integer, DatagramPacket>();
        destIP = InetAddress.getByName(remoteIPStr);
        destPort = remotePort;
        //TODO set up socket to listen for acks
        DatagramSocket outSocket = new DatagramSocket();
    }

    public static void main(String[] args)  {
        try {
        String infileName = args[0];
        String remoteIPStr = args[1];
        int remotePort = Integer.parseInt(args[2]);
        int ackPort = Integer.parseInt(args[3]);
        String logFileName = args[4];
        int windowSize = Integer.parseInt(args[5]);
        Sender tcplikeSender = new Sender(remoteIPStr, remotePort, ackPort, logfileName, windowSize);
        tcplikeSender.sendFile(infileName, logfileName);
        } catch (ArrayIndexOutOfBoundsException e)  {
            System.out.println("Usage: java Sender <filename> <remote_IP> <remote_port> <ack_port_num> <log_filename> <window_size>");
        }
   }

   private void sendFile(String infileName, String logfileName) {
       System.out.println("TODO sending ".concat(infileName));
   }
}
