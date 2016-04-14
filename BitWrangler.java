import java.util.BitSet;
import java.nio.ByteBuffer;

class BitWrangler  {
    public static int fromByteArray(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    public static byte[] toByteArray(int value) {
        return  ByteBuffer.allocate(4).putInt(value).array();
    }
    
    public static byte[] toByteArray(BitSet bits) {
        byte[] bytes = new byte[(bits.length() + 7) / 8];
        for (int i=0; i<bits.length(); i++) {
            if (bits.get(i)) {
                bytes[bytes.length-i/8-1] |= 1<<(i%8);
            }
        }
        return bytes;
    }

    public static byte[] toByteArray(int n, int numBytes)   {
        int numBits = 8 * numBytes;
        BitSet bits = new BitSet(numBits); 
        bits.clear();
        String s = String.format("%8s", Integer.toBinaryString(n)).replace(' ', '0');
        int margin = numBits - s.length();
        for (int i=0; i<s.length();i++) {
            if (s.charAt(i) == 49)
                bits.set(numBits - i - 1 - margin);
        }
        byte[] bytes = BitWrangler.toByteArray(bits);
        return bytes;
    }
    
    public static int toInt(byte[] bytes)   {
        if (bytes.length == 4)
            return BitWrangler.fromByteArray(bytes);
        else { 
            byte[] paddedBytes = {BitWrangler.toByteArray(0)[0],
                                    BitWrangler.toByteArray(0)[0],
                                    bytes[0],
                                    bytes[1]};
            return BitWrangler.fromByteArray(paddedBytes);
        }
    }

    public static void main(String[] args)  {
        byte[] bytes = BitWrangler.toByteArray(6000, 2); 
        /*
        byte b1 = bytes[0]; 
        byte b2 = bytes[1];
        String s1 = String.format("%8s", Integer.toBinaryString(b1 & 0xFF)).replace(' ', '0');
        String s2 = String.format("%8s", Integer.toBinaryString(b2 & 0xFF)).replace(' ', '0');
        System.out.print(s1);
        System.out.print(s2);
        */
        // convert byte[] to int
        int intVal = BitWrangler.toInt(bytes);
        System.out.println(intVal);
    }
}
