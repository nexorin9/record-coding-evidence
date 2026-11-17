package com.chaosforge.recordcoding.service;

/** 来源可达性结果（ok / unreachable / empty / error），带错误码与可读原因。 */
public record SourceOutcome(String source, String status, String errorCode, String reason, int itemCount) {
    public boolean isUnreachable() { return "unreachable".equals(status); }
    public boolean isEmpty() { return "empty".equals(status); }
    public boolean isOk() { return "ok".equals(status); }

    public static SourceOutcome ok(String source, int n) { return new SourceOutcome(source, "ok", "", "", n); }
    public static SourceOutcome empty(String source, String reason) {
        return new SourceOutcome(source, "empty", "", reason, 0);
    }
    public static SourceOutcome unreachable(String source, String code, String reason) {
        return new SourceOutcome(source, "unreachable", code, reason, 0);
    }
    public static SourceOutcome error(String source, String code, String reason) {
        return new SourceOutcome(source, "error", code, reason, 0);
    }
}
