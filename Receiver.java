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
    private DatagramSocket socket;
    private InetAddress senderIP;
    private int senderPort; 

    public Receiver(int listeningPort, String senderIPStr, int senderPort)   { 
        try {
            this.senderIP = InetAddress.getByName(senderIPStr);
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
        this.senderPort = senderPort;
    }

    private void writeFile(String outfileName, String logfileName)  {
        try {
            FileOutputStream fout = new FileOutputStream(new File(outfileName));
            DatagramPacket receivePacket;
            byte[] receiveData;
            while (true)  {
                receiveData = new byte[HEADERSIZE + MSS];
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                byte[] received = receivePacket.getData();
                int sourcePort = BitWrangler.toInt(Arrays.copyOfRange(received, 0, 2));
                int destPort = BitWrangler.toInt(Arrays.copyOfRange(received, 2, 4));
                int seqNum = BitWrangler.toInt(Arrays.copyOfRange(received, 4, 8));
                fout.write(Arrays.copyOfRange(received, HEADERSIZE, received.length));
            }
        } catch (FileNotFoundException e)   {
            System.out.println("specified output file exists but is a directory rather than a regular file, does not exist but cannot be created, or cannot be opened for any other reason");
            System.exit(0);
        } catch (IOException e) {
            System.out.println("I/O error occurred while reading from socket or writing to file");
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
