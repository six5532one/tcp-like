import java.util.HashMap;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

class Sender    {
    // before computing timeout interval dynamically, initialize to 5s
    public static final int INITIAL_TIMEOUT_INT = 5000;
    // maximum segment size is 576 bytes
    public static final int MSS = 576;
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
    private DatagramSocket outSocket;
    DatagramSocket ackSocket;

    public Sender(String remoteIPStr, int remotePort, int ackPort, int windowSize) {
        this.windowSize = windowSize;
        inTransitSendTimes = new HashMap<Integer, Long>();
        inTransitPackets = new HashMap<Integer, DatagramPacket>();
        /*
         * For host specified in literal IPv6 address, either the form defined in 
         * RFC 2732 or the literal IPv6 address format defined in RFC 2373 is accepted.
         * IPv6 scoped addresses are also supported.
         */
        try {
            destIP = InetAddress.getByName(remoteIPStr);
        } catch (UnknownHostException e)    {
            System.out.println("no IP address for the specified remote host could be found");
            System.exit(0);
        }
        destPort = remotePort;
        // Datagram socket for writing
        try {
            outSocket = new DatagramSocket();
            ackSocket = new DatagramSocket(ackPort);
        } catch (SocketException e) {
            System.out.println("socket could not be opened");
            System.exit(0);
        }
    }

    public static void main(String[] args)  {
        try {
            String infileName = args[0];
            String remoteIPStr = args[1];
            int remotePort = Integer.parseInt(args[2]);
            int ackPort = Integer.parseInt(args[3]);
            String logfileName = args[4];
            int windowSize = Integer.parseInt(args[5]);
            Sender tcplikeSender = new Sender(remoteIPStr, remotePort, ackPort, windowSize);
            tcplikeSender.sendFile(infileName, logfileName);
        } catch (ArrayIndexOutOfBoundsException e)  {
            System.out.println("Usage: java Sender <filename> <remote_IP> <remote_port> <ack_port_num> <log_filename> <window_size>");
        }
   }

   private void sendFile(String infileName, String logfileName) {
       byte[] sendData = new byte[MSS];
       try  {
           FileInputStream fileInput = new FileInputStream(new File(infileName));
           DatagramPacket sendPacket;
           int numBytesRead;
           while ((numBytesRead = fileInput.read(sendData)) > 0)    {
               sendPacket = new DatagramPacket(sendData, numBytesRead, destIP, destPort);
               outSocket.send(sendPacket);
           }
       } catch (FileNotFoundException e) {
           System.out.println("file does not exist, is a directory rather than a regular file, or for some other reason cannot be opened for reading");
           System.exit(0);
       } catch (IOException io) {
           System.out.println("I/O error occurred while reading from file or writing to socket");
           System.exit(0);
       }
   }
}
