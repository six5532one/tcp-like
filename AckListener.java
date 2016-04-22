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
        long receiveTime;
        try {
            while (true)  {
                receiveData = new byte[sender.HEADERSIZE];
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                sender.ackSocket.receive(receivePacket);
                byte[] received = receivePacket.getData();
                if (!BitWrangler.isCorrupted(received))  {
                    // extract ACK number
                    int ackNum = BitWrangler.toInt(Arrays.copyOfRange(received, 8, 12));
                    sender.lastAckReceived = ackNum; 
                    // extract source
                    int sourcePort = BitWrangler.toInt(Arrays.copyOfRange(received, 0, 2));
                    // extract dest
                    int destPort = BitWrangler.toInt(Arrays.copyOfRange(received, 2, 4));
                    Logger.log(sourcePort, destPort, 0, ackNum, false, true, sender.outstream);
                    if (ackNum > sender.getSendBase())  {
                        // stop current timer
                        sender.stopTimer();
                        receiveTime = System.currentTimeMillis();
                        // set sendBase to y
                        sender.updateSendBase(ackNum, receiveTime); 
                    }   // ACKed one or more in-transit packets
                    // still waiting for ack for sendBase
                    else if (ackNum == sender.getSendBase())    {
                        sender.numDupAcks++;
                        if (sender.numDupAcks == 3)    {
                            sender.retransmit(false);
                        }
                    }
                    synchronized (sender.LOCK)   {
                        sender.LOCK.notifyAll();
                    }   // released lock
                }   // handler for non-corrupt segments
            }   // done listening for ACKS
        }   catch (IOException io) {
                if (!sender.ackSocket.isClosed())
                    System.out.println("ack listener: I/O error occurred while reading from file or writing to socket");
        }
    }
}
