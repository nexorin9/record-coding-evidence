package com.chaosforge.recordcoding.transport;

/** 按对端能力配置是否回 ACK（0x06）/ NAK（0x15）；NAK 不丢消息，进重试队列（见 MllpRetryQueue）。 */
public final class MllpAckPolicy {
    public enum Mode { ACK, NAK, NONE }

    private final Mode mode;

    public MllpAckPolicy(Mode mode) { this.mode = mode; }

    public Mode mode() { return mode; }

    public byte[] ackBytes() { return ackBytes(mode); }

    public static byte[] ackBytes(Mode mode) {
        return switch (mode) {
            case ACK -> new byte[] {MllpBytes.ACK};
            case NAK -> new byte[] {MllpBytes.NAK};
            case NONE -> new byte[0];
        };
    }

    public byte[] ackFor(boolean accepted) {
        return mode == Mode.NONE ? new byte[0] : ackBytes(accepted ? Mode.ACK : Mode.NAK);
    }
}
