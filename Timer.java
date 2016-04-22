import java.net.DatagramSocket;
import java.net.SocketException;

class Timer extends Thread {
    public static final int rate = 5;   // 5 milliseconds
    private Sender sender;
    private long duration;
    private long elapsed;
    private boolean stop;
    private boolean isRunning;
    
    public Timer(Sender s, long duration)  {
        this.duration = duration;
        sender = s;
        elapsed = 0;
        stop = false;
        isRunning = false;
    }
    
    public synchronized void stopTimer() {
        stop = true;
        isRunning = false;
    }

    public void run()   {
        isRunning = true;
        while (!stop)   {
            if (Thread.interrupted())
                return;
            try {
                Thread.sleep(rate);
            }   catch (InterruptedException ioe)    {
                continue;
            }
            synchronized ( this )   {
                elapsed += rate;
                if (elapsed > duration) {
                    timeout();
                    break;
                }
            }
        }
    }

    public boolean isRunning()  {
        return isRunning;
    }

    public void timeout()   {
        stopTimer();
        try {
            sender.retransmit(true);
        }   catch (NullPointerException ne) {
        }
    }
}
