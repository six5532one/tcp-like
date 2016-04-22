import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

class Sender    {
    // before computing timeout interval dynamically, initialize to 5s
    public static final long BOOTSTRAP_TIMEOUT = 1000;
    // maximum segment size is 576 bytes
    public static final int MSS = 576;
    public static final int HEADERSIZE = 20;
    private int numSegmentsSent, numSegmentsRetransmitted, numBytesSent;
    int nextSeqNum = 0;
    int numDupAcks = 0;
    int lastAckReceived = -1;
    private HashMap<Integer, Long> inTransitSendTimes;
    private HashMap<Integer, DatagramPacket> inTransitPackets;
    private long estimatedRTT = -1;
    private long devRTT = -1;
    // provide a limited form of congestion control
    private long retransmissionTimeoutInt = -1; 
    private int windowSize;
    private InetAddress destIP;
    private int destPort, ackPort, sourcePort;
    private DatagramSocket outSocket;
    DatagramSocket ackSocket;
    Timer timer;
    Object LOCK;
    ArrayList<Thread> threads;
    OutputStream outstream;

    synchronized void stopTimer()   {
        timer.stopTimer();
    }
    synchronized void startTimer(long timeout)  {
        timer = new Timer(this, timeout);
        Thread t = new Thread(timer);
        threads.add(t);
        t.start();
    }
    private long getTimeoutInt()    {
        long result;
        if (estimatedRTT < 0 || devRTT < 0)
            result = BOOTSTRAP_TIMEOUT;
        else
            result = estimatedRTT + 4 * devRTT;
        return result;
    }

    private void updateEstimatedRTT(long sample)    {
        if (estimatedRTT < 0)
            estimatedRTT = sample;
        else
            estimatedRTT = (long)(0.875 * estimatedRTT) + (long)(0.125 * sample);
    }

    private void updateDevRTT(long sample)  {
        if (estimatedRTT > 0)   {
            if (devRTT < 0)
                devRTT = Math.abs(estimatedRTT - sample);
            else
                devRTT = (long)(0.75 * devRTT) + (long)(0.25 * Math.abs(estimatedRTT - sample));
        }
    }

    void updateRTTMeasurements(long sample) {
        // update Estimated RTT, DevRTT
        updateEstimatedRTT(sample);
        updateDevRTT(sample);
    }

    public Sender(String remoteIPStr, int remotePort, int ackPort, int windowSize, String logfileName) {
        threads = new ArrayList<Thread>();
        numSegmentsSent = 0;
        numSegmentsRetransmitted = 0;
        numBytesSent = 0;
        timer = new Timer(this, BOOTSTRAP_TIMEOUT);
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
        LOCK = new Object();
        if (logfileName.equals("stdout"))
            outstream = System.out;
        else    {
            try {
                File logFile = new File(logfileName);
                outstream = new FileOutputStream(logFile);
            } catch (FileNotFoundException e)   {
                System.err.println("logfile not found");
            }
        }
        // Datagram socket for writing
        try {
            // bind to this port for testing on link simulator
            outSocket = new DatagramSocket(5555);
            sourcePort = outSocket.getLocalPort();
            ackSocket = new DatagramSocket(ackPort);
            AckListener ackListener = new AckListener(this);
            Thread t = new Thread(ackListener);
            threads.add(t);
            t.start();
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
    
    private String getStatString() {
        StringBuilder sb = new StringBuilder("Total bytes sent = ");
        sb.append(numBytesSent).append("\nSegments sent = ")
        .append(numSegmentsSent).append("\nSegments retransmitted = ")
        .append((double)numSegmentsRetransmitted/numSegmentsSent)
        .append("%");
        return sb.toString();
    }

    public static void main(String[] args)  {
        try {
            String infileName = args[0];
            String remoteIPStr = args[1];
            int remotePort = Integer.parseInt(args[2]);
            int ackPort = Integer.parseInt(args[3]);
            String logfileName = args[4];
            int windowSize = Integer.parseInt(args[5]);
            Sender tcplikeSender = new Sender(remoteIPStr, remotePort, ackPort, windowSize, logfileName);
            boolean succeeded = tcplikeSender.sendFile(infileName, logfileName);
            String result;
            if (succeeded)
                result = "Delivery completed successfully\n";
            else
                result = "Delivery failed\n";
            System.out.println(result + tcplikeSender.getStatString());
        } catch (ArrayIndexOutOfBoundsException e)  {
            System.out.println("Usage: java Sender <filename> <remote_IP> <remote_port> <ack_port_num> <log_filename> <window_size>");
        }
   }
   
   synchronized int getSendBase()    {
       // return the sequence number of the oldest sent, unacknowledged packet
       Integer oldest = nextSeqNum;
       for (Integer sn: inTransitPackets.keySet())  {
           if (sn < oldest)
               oldest = sn;
       }
       return oldest.intValue();
   }

   synchronized void updateSendBase(int newSendBase, long receiveTime)  {
       int oldSendBase = getSendBase();
       Long sentTime = inTransitSendTimes.get(oldSendBase);
       long RTTSample = receiveTime - sentTime;
       updateRTTMeasurements(RTTSample);
       for (int sn = oldSendBase; sn < newSendBase; sn++)  {
           inTransitSendTimes.remove(sn);
           inTransitPackets.remove(sn);
       }
       retransmissionTimeoutInt = getTimeoutInt();
       numDupAcks = 0;
       // restart timer if any packets in transit
       if (inTransitPackets.size() > 0)
           startTimer(getTimeoutInt());
   }

   private boolean fallsInWindow()  {
       return nextSeqNum < getSendBase() + windowSize;
   }

   synchronized void updateWindow(int justSentSeqNum, long sendTime, DatagramPacket justSentPacket)  {
       inTransitSendTimes.put(justSentSeqNum, sendTime);
       inTransitPackets.put(justSentSeqNum, justSentPacket);
   }

   synchronized void retransmit(boolean isTimeout)   {
       long timeout;
       if (isTimeout)   {
           retransmissionTimeoutInt *= (long)1.5;
           timeout = retransmissionTimeoutInt;
       }
       else {
           stopTimer();
           timeout = getTimeoutInt();
       }
       int seqNumToResend = getSendBase();
       DatagramPacket toRetransmit = inTransitPackets.get(seqNumToResend);
       try  {
           outSocket.send(toRetransmit);
           Logger.log(sourcePort, destPort, seqNumToResend, 0, false, false, outstream);
           numSegmentsRetransmitted++;
           numSegmentsSent++;
           numBytesSent += toRetransmit.getLength();
           long sendTime = System.currentTimeMillis();
           if (timer.isRunning())
               stopTimer();
           startTimer(timeout);
           inTransitSendTimes.put(seqNumToResend, sendTime);
           numDupAcks = 0;
       } catch (IOException io) {
           if (!outSocket.isClosed())   {
               System.out.println("SenderThread: I/O error occurred while writing to socket");
               System.exit(0);
           }
       }
   }

   private synchronized void send(DatagramPacket packet) {
       retransmissionTimeoutInt = getTimeoutInt();
       if (!timer.isRunning())  {    
           // start timer
           startTimer(getTimeoutInt());
       }
       try  {
           outSocket.send(packet);
           long sendTime = System.currentTimeMillis();
           updateWindow(nextSeqNum, sendTime, packet); 
           nextSeqNum++;
           numSegmentsSent++;
           numBytesSent += packet.getLength();
       }    catch (IOException io) {
           if (!outSocket.isClosed())   {
               System.out.println("SenderThread: I/O error occurred while reading from file or writing to socket");
               System.exit(0);
           }
       }
   }

   boolean doneSending()    {
       return lastAckReceived >= nextSeqNum;
   }

   private boolean sendFile(String infileName, String logfileName) {
       byte[] payload = new byte[MSS];
       byte[] header;
       byte[] sendData = new byte[HEADERSIZE + MSS];
       try  {
           FileInputStream fileInput = new FileInputStream(new File(infileName));
           DatagramPacket sendPacket;
           int numBytesRead;
           while ((numBytesRead = fileInput.read(payload)) > 0)    {
               // wait for fallsInWindow() to return true
               synchronized (LOCK) {
                   while (!fallsInWindow())    {
                       try { LOCK.wait(); }
                       catch (InterruptedException e) {
                           break;
                       }
                   }
               }
               header = getHeader();
               // copy header data into `sendData`
               for (int i=0; i<HEADERSIZE; i++)
                   sendData[i] = header[i];
               // copy payload data into `sendData`
               for (int i=0; i<MSS; i++)
                   sendData[HEADERSIZE + i] = payload[i];
               // TODO set checksum header field
               sendPacket = new DatagramPacket(sendData, sendData.length, destIP, destPort); 
               Logger.log(sourcePort, destPort, nextSeqNum, 0, false, false, outstream);
               send(sendPacket);
               payload = new byte[MSS];
               sendData = new byte[MSS + HEADERSIZE];
           }
           // wait for ACK of last used sequence number
           synchronized (LOCK) {
               while (!(doneSending()))    {
                   try { LOCK.wait(); }
                   catch (InterruptedException e) {
                       break;
                   }
               }
           }
           // send FIN request
           header = getHeader();
           // set FIN header field to 1
           BitSet bits = new BitSet(8);
           bits.set(0);
           header[13] = BitWrangler.toByteArray(bits)[0];
           sendPacket = new DatagramPacket(header, header.length, destIP, destPort);
           Logger.log(sourcePort, destPort, nextSeqNum, 0, true, false, outstream);
           send(sendPacket); 
           // wait for receiver's shutdown segment
           DatagramPacket finACK = new DatagramPacket(header, header.length);
           outSocket.receive(finACK);   
           //stopTimer();
           for (Thread t: threads)
               t.interrupt();
           outSocket.close();
           ackSocket.close();
           return true;
       } catch (FileNotFoundException e) {
           System.out.println("file does not exist, is a directory rather than a regular file, or for some other reason cannot be opened for reading");
           return false;
       } catch (IOException io) {
           if (!outSocket.isClosed())   {
               System.out.println("SenderThreadBottom: I/O error occurred while reading from file or writing to socket");
               return false;
           }
           else
               return true;
       }
   }
}
