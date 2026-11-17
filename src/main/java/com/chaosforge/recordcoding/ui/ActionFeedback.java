package com.chaosforge.recordcoding.ui;

import java.util.List;

/** 采纳 / 驳回 / 提交 / 导出四类动作的成功与失败反馈；失败不静默改数据。 */
public final class ActionFeedback {
    public record Feedback(String action, boolean success, String message, boolean dataChanged) {}

    public static final List<String> ACTIONS = List.of("采纳", "驳回", "提交", "导出");

    private ActionFeedback() {}

    public static Feedback of(String action, boolean success, String detail) {
        String text = success ? action + "成功：" + detail : action + "失败：" + detail + "（界面与数据保持原样）";
        return new Feedback(action, success, text, success);
    }

    public static List<Feedback> allOutcomes() {
        return ACTIONS.stream().flatMap(a -> java.util.stream.Stream.of(of(a, true, "已完成"),
                of(a, false, "对端拒绝"))).toList();
    }
}
