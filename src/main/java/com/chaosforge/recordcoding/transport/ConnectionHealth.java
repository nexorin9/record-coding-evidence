package com.chaosforge.recordcoding.transport;

import java.time.Instant;

/** 连接状态 / 待处理条数 / 最近错误 / 重试队列长度，供托盘 tooltip 与状态栏读取。 */
public final class ConnectionHealth {
    public enum Status { AVAILABLE, UNAVAILABLE, DEGRADED }

    private volatile Status status = Status.UNAVAILABLE;
    private volatile int pendingCount;
    private volatile int retryQueueLength;
    private volatile String lastErrorCode = "";
    private volatile String lastErrorTime = "";
    private volatile int submittedToday;

    public void markAvailable() { status = Status.AVAILABLE; }

    public void markUnavailable(String errorCode) {
        status = Status.UNAVAILABLE;
        recordError(errorCode);
    }

    public void markDegraded(String errorCode) {
        status = Status.DEGRADED;
        recordError(errorCode);
    }

    private void recordError(String code) {
        if (code != null && !code.isBlank()) {
            lastErrorCode = code;
            lastErrorTime = Instant.now().toString();
        }
    }

    public Status status() { return status; }
    public int pendingCount() { return pendingCount; }
    public int retryQueueLength() { return retryQueueLength; }
    public String lastErrorCode() { return lastErrorCode; }
    public String lastErrorTime() { return lastErrorTime; }
    public int submittedToday() { return submittedToday; }

    public void setPendingCount(int n) { pendingCount = n; }
    public void setRetryQueueLength(int n) { retryQueueLength = n; }
    public void setSubmittedToday(int n) { submittedToday = n; }

    public String tooltip() {
        return "连接 " + status + " · 待处理 " + pendingCount + " · 今日已提交 " + submittedToday
                + (lastErrorCode.isBlank() ? "" : " · 最近错误 " + lastErrorCode);
    }
}
