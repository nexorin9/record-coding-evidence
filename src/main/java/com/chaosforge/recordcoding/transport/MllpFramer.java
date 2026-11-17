package com.chaosforge.recordcoding.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** MLLP 分帧：0x0B 起始 / 0x1C 0x0D 结束；支持一帧多消息、半包粘包与三类帧合法性错误。 */
public final class MllpFramer {
    public static final int DEFAULT_MAX_FRAME_BYTES = 1024 * 1024;

    public record FramingResult(List<byte[]> frames, byte[] remainder, String errorCode, String message) {
        public boolean hasError() { return errorCode != null; }
    }

    private final int maxFrameBytes;

    public MllpFramer() { this(DEFAULT_MAX_FRAME_BYTES); }

    public MllpFramer(int maxFrameBytes) { this.maxFrameBytes = maxFrameBytes; }

    public FramingResult decode(byte[] input) {
        return decode(input, new byte[0]);
    }

    /** pending 为上次遗留的半包，拼在当前输入前继续解析。 */
    public FramingResult decode(byte[] input, byte[] pending) {
        byte[] data = concat(pending, input == null ? new byte[0] : input);
        List<byte[]> frames = new ArrayList<>();
        int cursor = 0;
        while (cursor < data.length) {
            if (data[cursor] != MllpBytes.START) {
                return new FramingResult(frames, new byte[0], "FRAME_START_MISSING",
                        "位置 " + cursor + " 缺少起始字节 0x0B");
            }
            int end = -1;
            for (int i = cursor + 1; i < data.length - 1; i++) {
                if (data[i] == MllpBytes.END && data[i + 1] == MllpBytes.CR) { end = i; break; }
            }
            if (end < 0) {
                if (data.length - cursor > maxFrameBytes) {
                    return new FramingResult(frames, new byte[0], "FRAME_TOO_LONG",
                            "半包长度 " + (data.length - cursor) + " 超过上限 " + maxFrameBytes);
                }
                if (data.length >= 2 && data[data.length - 1] == MllpBytes.END) {
                    return new FramingResult(frames, new byte[0], "FRAME_TRUNCATED",
                            "结束字节仅有 0x1C，缺 0x0D");
                }
                return new FramingResult(frames, Arrays.copyOfRange(data, cursor, data.length), null, "");
            }
            int length = end - cursor - 1;
            if (length > maxFrameBytes) {
                return new FramingResult(frames, new byte[0], "FRAME_TOO_LONG",
                        "帧长 " + length + " 超过上限 " + maxFrameBytes);
            }
            frames.add(Arrays.copyOfRange(data, cursor + 1, end));
            cursor = end + 2;
        }
        return new FramingResult(frames, new byte[0], null, "");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
