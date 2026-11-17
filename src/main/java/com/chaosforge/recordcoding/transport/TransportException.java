package com.chaosforge.recordcoding.transport;

/** 五类出站错误码：连接失败 / 超时 / 4xx / 5xx / 结构校验失败（外加凭据缺失）。 */
public class TransportException extends RuntimeException {
    public enum ErrorCode { CONNECT_FAILED, TIMEOUT, HTTP_4XX, HTTP_5XX, STRUCTURE_INVALID, TOKEN_MISSING }

    private final ErrorCode code;
    private final int status;

    public TransportException(ErrorCode code, String message) { this(code, message, 0); }

    public TransportException(ErrorCode code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ErrorCode code() { return code; }
    public int status() { return status; }

    /** 仅 5xx 与超时可退避重试；4xx / 结构失败 / 连接失败不重试。 */
    public boolean retryable() { return code == ErrorCode.TIMEOUT || code == ErrorCode.HTTP_5XX; }

    public String readableReason() {
        return switch (code) {
            case CONNECT_FAILED -> "连接失败：" + getMessage();
            case TIMEOUT -> "请求超时：" + getMessage();
            case HTTP_4XX -> "对端拒绝（4xx，状态 " + status + "）：" + getMessage();
            case HTTP_5XX -> "对端服务异常（5xx，状态 " + status + "）：" + getMessage();
            case STRUCTURE_INVALID -> "响应结构校验失败：" + getMessage();
            case TOKEN_MISSING -> "凭据缺失：" + getMessage();
        };
    }
}
