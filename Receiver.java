import java.util.HashMap;
import java.util.BitSet;
import java.util.Arrays;
import java.net.DatagramPacket;
import java.io.IOException;
import java.net.SocketException;
import java.net.DatagramSocket;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.File;

class Receiver  {
    public static final int MSS = 576;
    public static final int HEADERSIZE = 20;
    private int nextExpected = 0;
    private HashMap<Integer, byte[]> buffer;
    private DatagramSocket socket;
    private InetAddress destIP;
    private int destPort, sourcePort; 

    public Receiver(int listeningPort, String senderIPStr, int senderPort)   { 
        try {
            this.destIP = InetAddress.getByName(senderIPStr);
        } catch (UnknownHostException e)    {
            System.out.println("no IP address for the specified remote host could be found");
            System.exit(0);
        }
        try {
            socket = new DatagramSocket(listeningPort);
        } catch (SocketException e) {
            System.out.println("socket could not be opened, or the socket could not bind to the specified listening port");
            System.exit(0);
        }
        this.destPort = senderPort;
        this.sourcePort = listeningPort;
        buffer = new HashMap<Integer, byte[]>();
    }

    private boolean isFinSegment(byte[] headers)  {
        byte b = headers[13];
        return (b & 1) != 0;
    }

    private byte[] getHeader()    {
        // write source port
        byte[] sourcePortField = BitWrangler.toByteArray(sourcePort, 2);
        // write destination port
        byte[] destPortField = BitWrangler.toByteArray(destPort, 2);
        // write ACK number
        byte[] ackNumField = BitWrangler.toByteArray(nextExpected, 4);
        // set header length field
        BitSet bits = new BitSet(8);
        bits.set(6);
        bits.set(4);
        byte headerLength = BitWrangler.toByteArray(bits)[0];
        // set ACK flag
        BitSet flagBits = new BitSet(8);
        flagBits.set(4);
        byte flags = BitWrangler.toByteArray(flagBits)[0];
        byte[] header = new byte[HEADERSIZE];
        header[0] = sourcePortField[0];
        header[1] = sourcePortField[1];
        header[2] = destPortField[0];
        header[3] = destPortField[1];
        header[8] = ackNumField[0];
        header[9] = ackNumField[1];
        header[10] = ackNumField[2];
        header[11] = ackNumField[3];
        header[12] = headerLength;
        header[13] = flags;
        return header;
    }

    private void writeFile(String outfileName, String logfileName)  {
        try {
            FileOutputStream fout = new FileOutputStream(new File(outfileName));
            DatagramPacket receivePacket;
            byte[] receiveData;
            byte[] header;
            while (true)  {
                receiveData = new byte[HEADERSIZE + MSS];
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                byte[] received = receivePacket.getData();
                if (!BitWrangler.isCorrupted(received)) {
                    if (isFinSegment(Arrays.copyOfRange(received, 0, HEADERSIZE))) {
                        System.out.println("Received fin segment");
                        // find out which port the FIN segment came from
                        byte[] FINSourceBytes = Arrays.copyOfRange(received, 0, 2);
                        int FINSourcePort = BitWrangler.toInt(FINSourceBytes);
                        // respond with ACK
                        header = getHeader();
                        DatagramPacket finACK = new DatagramPacket(header, header.length, destIP, FINSourcePort);
                        socket.send(finACK);
                        break;
                    }
                    int sourcePort = BitWrangler.toInt(Arrays.copyOfRange(received, 0, 2));
                    int destPort = BitWrangler.toInt(Arrays.copyOfRange(received, 2, 4));
                    int seqNum = BitWrangler.toInt(Arrays.copyOfRange(received, 4, 8));
                    byte[] payload = Arrays.copyOfRange(received, HEADERSIZE, received.length);
                    if (seqNum > nextExpected)  {
                        //buffer out-of-order packet
                        buffer.put(seqNum, payload);
                    }
                    else if (seqNum == nextExpected)    {
                        fout.write(payload);
                        // write buffered, contiguous packets to outfile
                        byte[] toWrite;
                        int receivedSeqNum = seqNum + 1;
                        while (true)    {
                            toWrite = buffer.remove(receivedSeqNum);
                            if (toWrite == null)    {
                                nextExpected = receivedSeqNum;
                                break;
                            }
                            fout.write(toWrite);
                            receivedSeqNum++;
                        }   //done writing buffered data and updating buffer
                    }
                }   // not corrupted
                sendACK(socket);
            }
        } catch (FileNotFoundException e)   {
            System.out.println("specified output file exists but is a directory rather than a regular file, does not exist but cannot be created, or cannot be opened for any other reason");
            System.exit(0);
        } catch (IOException e) {
            System.out.println("I/O error occurred while reading from socket or writing to file");
            System.exit(0);
        }
    }

    private void sendACK(DatagramSocket socket)    {
        byte[] header = getHeader();
        DatagramPacket ack = new DatagramPacket(header, header.length, destIP, destPort);
        System.out.println("sending ack");
        try {
            socket.send(ack);
        } catch (IOException e) {
            System.out.println("I/O error occurred while sending ACK. Shutting down...");
            System.exit(0);
        }
    }

    public static void main(String[] args)  {
        try {
            String outfileName = args[0];
            int listeningPort = Integer.parseInt(args[1]);
            String senderIPStr = args[2];
            int senderPort = Integer.parseInt(args[3]);
            String logfileName = args[4];
            Receiver tcplikeReceiver = new Receiver(listeningPort, senderIPStr, senderPort);
            tcplikeReceiver.writeFile(outfileName, logfileName);
        } catch (ArrayIndexOutOfBoundsException e)  {
            System.out.println("Usage: java Receiver <filename> <listening_port> <sender_IP> <sender_port> <log_filename>");
        } 
    }
}
