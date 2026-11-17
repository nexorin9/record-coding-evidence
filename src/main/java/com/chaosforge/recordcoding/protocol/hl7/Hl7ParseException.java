package com.chaosforge.recordcoding.protocol.hl7;

/** 严格分词异常：带错误码与段号，不跳段继续。 */
public class Hl7ParseException extends RuntimeException {
    private final String code;
    private final int segmentIndex;

    public Hl7ParseException(String code, String message, int segmentIndex) {
        super(message);
        this.code = code;
        this.segmentIndex = segmentIndex;
    }

    public String code() { return code; }
    public int segmentIndex() { return segmentIndex; }
}
