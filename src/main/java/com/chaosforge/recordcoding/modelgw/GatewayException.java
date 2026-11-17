package com.chaosforge.recordcoding.modelgw;

/** 模型网关五类错误码，各带可读原因与 retryable 标记。 */
public class GatewayException extends RuntimeException {
    public enum ErrorCode { CONNECT_FAILED, TIMEOUT, HTTP_4XX, HTTP_5XX, STRUCTURE_INVALID }

    private final ErrorCode code;

    public GatewayException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() { return code; }

    public boolean retryable() { return code == ErrorCode.TIMEOUT || code == ErrorCode.HTTP_5XX; }

    public String readableReason() { return code + "：" + getMessage(); }

    public static GatewayException from(com.chaosforge.recordcoding.transport.TransportException e) {
        ErrorCode code = switch (e.code()) {
            case CONNECT_FAILED -> ErrorCode.CONNECT_FAILED;
            case TIMEOUT -> ErrorCode.TIMEOUT;
            case HTTP_4XX, TOKEN_MISSING -> ErrorCode.HTTP_4XX;
            case HTTP_5XX -> ErrorCode.HTTP_5XX;
            case STRUCTURE_INVALID -> ErrorCode.STRUCTURE_INVALID;
        };
        return new GatewayException(code, e.readableReason());
    }
}
