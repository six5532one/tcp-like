import java.net.DatagramSocket;
import java.net.SocketException;

class Timer extends Thread {
    public static final int rate = 5;   // 5 milliseconds
    private Sender sender;
    private TimerTest timerTest;
    private TestTimerIsRunning ttr;
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
    public Timer(TimerTest tt, long duration)  {
        this.duration = duration;
        timerTest = tt;
        elapsed = 0;
        stop = false;
        isRunning = false;
    }
    
    public Timer(TestTimerIsRunning tt, int duration)  {
        this.duration = duration;
        ttr = tt;
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
        //System.out.println(elapsed);
        //return (elapsed < duration) && (!stop); 
        return isRunning;
    }

    public void timeout()   {
        /*
        isRunning = false;
        timerTest.inWindow = true;
        synchronized (ttr.LOCK)   {
            ttr.LOCK.notifyAll();
        }
        */
        stopTimer();
        System.err.println ("Network timeout");
        sender.retransmit(true);
    }
}
