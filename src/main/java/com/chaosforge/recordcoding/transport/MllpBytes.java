package com.chaosforge.recordcoding.transport;

/** MLLP 字节常量集中一处，禁止散落在业务代码。 */
public final class MllpBytes {
    public static final byte START = 0x0B;
    public static final byte END = 0x1C;
    public static final byte CR = 0x0D;
    public static final byte ACK = 0x06;
    public static final byte NAK = (byte) 0x15;

    private MllpBytes() {}

    public static byte[] wrap(byte[] message) {
        byte[] out = new byte[message.length + 3];
        out[0] = START;
        System.arraycopy(message, 0, out, 1, message.length);
        out[out.length - 2] = END;
        out[out.length - 1] = CR;
        return out;
    }
}
