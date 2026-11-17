package com.chaosforge.recordcoding.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** 运行摘要：连接状态 / 待处理条数 / 今日已提交数 / 降级态 / 最近错误，供托盘与 conn-check 共用。 */
public final class RuntimeSummary {
    public record Snapshot(String connectionStatus, int pendingCount, int submittedToday,
                           boolean degraded, String lastErrorCode, String lastErrorTime) {}

    private RuntimeSummary() {}

    public static Snapshot of(String status, int pending, int submittedToday, boolean degraded,
                              String lastErrorCode, String lastErrorTime) {
        return new Snapshot(status, pending, submittedToday, degraded, lastErrorCode, lastErrorTime);
    }

    public static Map<String, Object> fields(Snapshot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("connection_status", s.connectionStatus());
        m.put("pending_count", s.pendingCount());
        m.put("submitted_today", s.submittedToday());
        m.put("degraded", s.degraded());
        m.put("last_error_code", s.lastErrorCode() == null ? "" : s.lastErrorCode());
        m.put("last_error_time", s.lastErrorTime() == null ? "" : s.lastErrorTime());
        return m;
    }

    public static String render(Snapshot s) {
        return "已连接 " + s.connectionStatus() + " · 待处理 " + s.pendingCount()
                + " · 今日已提交 " + s.submittedToday()
                + (s.degraded() ? " · 降级模式" : "")
                + (s.lastErrorCode() != null && !s.lastErrorCode().isBlank() ? " · 最近错误 " + s.lastErrorCode() : "");
    }
}
