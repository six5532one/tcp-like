import java.util.Arrays;
import java.io.IOException;
import java.net.DatagramPacket;

public class AckListener extends Thread {
    private Sender sender;
    
    public AckListener(Sender s)    {
        sender = s;
    }

    public void run()    {
        DatagramPacket receivePacket;
        byte[] receiveData;
        try {
            while (true)  {
                receiveData = new byte[sender.HEADERSIZE];
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                sender.ackSocket.receive(receivePacket);
                byte[] received = receivePacket.getData();
                if (!BitWrangler.isCorrupted(received))  {
                    // TODO extract ACK number
                    int ackNum = BitWrangler.toInt(Arrays.copyOfRange(received, 8, 12));
                    System.out.println(ackNum);
                }
            }
        }   catch (IOException io) {
            System.out.println("ack listener: I/O error occurred while reading from file or writing to socket");
            System.exit(0);
        }
    }
}

