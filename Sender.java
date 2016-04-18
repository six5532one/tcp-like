import java.util.BitSet;
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
    public static final int HEADERSIZE = 20;
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
    private int destPort, ackPort, sourcePort;
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
            // bind to this port for testing on link simulator
            outSocket = new DatagramSocket(5555);
            sourcePort = outSocket.getLocalPort();
            ackSocket = new DatagramSocket(ackPort);
        } catch (SocketException e) {
            System.out.println("socket could not be opened");
            System.exit(0);
        }
    }

    private byte[] getHeader()  {
        // write source port
        byte[] sourcePortField = BitWrangler.toByteArray(sourcePort, 2);
        // write destination port
        byte[] destPortField = BitWrangler.toByteArray(destPort, 2);
        // write sequence number
        byte[] seqNumField = BitWrangler.toByteArray(nextSeqNum, 4);
        // set header length field
        BitSet bits = new BitSet(8);
        bits.set(6);
        bits.set(4);
        byte headerLength = BitWrangler.toByteArray(bits)[0]; 
        byte[] header = new byte[HEADERSIZE];
        header[0] = sourcePortField[0];
        header[1] = sourcePortField[1];
        header[2] = destPortField[0];
        header[3] = destPortField[1];
        header[4] = seqNumField[0];
        header[5] = seqNumField[1];
        header[6] = seqNumField[2];
        header[7] = seqNumField[3];
        header[12] = headerLength;
        return header;
    }
    
    private void displayStats() {
        System.out.println("TODO print stats at end");
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
            tcplikeSender.displayStats();
        } catch (ArrayIndexOutOfBoundsException e)  {
            System.out.println("Usage: java Sender <filename> <remote_IP> <remote_port> <ack_port_num> <log_filename> <window_size>");
        }
   }

   private void sendFile(String infileName, String logfileName) {
       byte[] payload = new byte[MSS];
       byte[] header;
       byte[] sendData = new byte[HEADERSIZE + MSS];
       try  {
           FileInputStream fileInput = new FileInputStream(new File(infileName));
           DatagramPacket sendPacket;
           int numBytesRead;
           while ((numBytesRead = fileInput.read(payload)) > 0)    {
               header = getHeader();
               // copy header data into `sendData`
               for (int i=0; i<HEADERSIZE; i++)
                   sendData[i] = header[i];
               // copy payload data into `sendData`
               for (int i=0; i<MSS; i++)
                   sendData[HEADERSIZE + i] = payload[i];
               // TODO set checksum header field
               sendPacket = new DatagramPacket(sendData, sendData.length, destIP, destPort);
               outSocket.send(sendPacket);
               nextSeqNum++;
               payload = new byte[MSS];
               sendData = new byte[MSS + HEADERSIZE];
           }
           // send FIN request
           header = getHeader();
           // set FIN header field to 1
           BitSet bits = new BitSet(8);
           bits.set(0);
           header[13] = BitWrangler.toByteArray(bits)[0];
           sendPacket = new DatagramPacket(header, header.length, destIP, destPort);
           outSocket.send(sendPacket);
           // wait for receiver's shutdown segment
           DatagramPacket finACK = new DatagramPacket(header, header.length);
           outSocket.receive(finACK);   
           System.out.println("received finACK. closing socket...");
           outSocket.close();
       } catch (FileNotFoundException e) {
           System.out.println("file does not exist, is a directory rather than a regular file, or for some other reason cannot be opened for reading");
           System.exit(0);
       } catch (IOException io) {
           System.out.println("I/O error occurred while reading from file or writing to socket");
           System.exit(0);
       }
   }
}
