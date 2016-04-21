import java.util.Arrays;
import java.io.IOException;
import java.net.DatagramPacket;

public class AckListener extends Thread {
    private Sender sender;
    int numDupAcks;

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
                    System.out.println("Received ACK " + Integer.toString(ackNum));
                    if (ackNum > sender.getSendBase())  {
                        // stop current timer
                        sender.stopTimer();
                        System.out.println("AckListener stopped timer");
                        System.out.println(sender.timer.isRunning());
                        receiveTime = System.currentTimeMillis();
                        // set sendBase to y
                        sender.updateSendBase(ackNum, receiveTime);
                        synchronized (sender.LOCK)   {
                            sender.LOCK.notifyAll();
                        }
                    }   // ACKed one or more in-transit packets
                    // still waiting for ack for sendBase
                    else if (ackNum == sender.getSendBase())    {
                        sender.numDupAcks++;
                        System.out.println("num dup ACKS: " + Integer.toString(numDupAcks));
                        if (sender.numDupAcks == 3)    {
                            sender.retransmit(false);
                            //startTimer(getTimeoutInt(EstimatedRTT, DevRTT))
                        }
                    }
                }
            }
        }   catch (IOException io) {
            System.out.println("ack listener: I/O error occurred while reading from file or writing to socket");
            System.exit(0);
        }
    }
}

