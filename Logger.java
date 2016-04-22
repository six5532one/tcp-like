import java.io.IOException;
import java.io.OutputStream;

class Logger    {
    private static final int URG = 0;
    private static final int PSH = 0;
    private static final int RST = 0;
    private static final int SYN = 0;
    private static final String delim = ", ";

    public static void log(int sourcePort, int destPort, int seqNum, int ackNum, boolean isFIN, boolean isACK, OutputStream outstream) {
        int ACK = isACK ? 1 : 0;
        int FIN = isFIN ? 1 : 0;
        StringBuilder sb = new StringBuilder();
        sb.append(System.currentTimeMillis()).append(delim).append(sourcePort)
            .append(delim).append(destPort).append(delim).append(seqNum).append(delim)
            .append(ackNum).append(delim).append(URG).append(delim).append(ACK)
            .append(delim).append(PSH).append(delim).append(RST).append(delim)
            .append(SYN).append(delim).append(FIN).append("\n");
        try {
        outstream.write(sb.toString().getBytes());
        } catch (IOException e) {
            System.out.println("I/O Exception occurred while logging to output stream");
        }
    }
}
