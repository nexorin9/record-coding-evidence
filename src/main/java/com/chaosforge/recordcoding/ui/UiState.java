package com.chaosforge.recordcoding.ui;

import java.util.List;

/** 加载 / 空 / 错误三态与操作反馈文案：三类空态互不相同，错误态给错误码 + 可读原因 + 处理建议。 */
public final class UiState {
    public enum LoadingStage {
        FETCH("取回证据"), ASSEMBLE("装配对照表"), JUDGE("语义判定"), CONFIRM("等待确认");
        private final String label;
        LoadingStage(String label) { this.label = label; }
        public String label() { return label; }
    }

    public enum EmptyKind { NO_EVIDENCE, NO_DIFFERENCE, DICTIONARY_UNAVAILABLE }

    private UiState() {}

    public static List<String> loadingStages() {
        return java.util.Arrays.stream(LoadingStage.values()).map(LoadingStage::label).toList();
    }

    public static String loadingText(LoadingStage stage, int percent) {
        return "复核进行中 · " + stage.label() + " · " + percent + "%";
    }

    public static String emptyText(EmptyKind kind) {
        return switch (kind) {
            case NO_EVIDENCE -> "本次未取回任何影像证据：请检查 PACS 与 RIS 通道是否已接入，或稍后重试。";
            case NO_DIFFERENCE -> "本次未发现编码与证据不符：首页编码与报告证据一致，可直接归档。";
            case DICTIONARY_UNAVAILABLE -> "官方编码字典当前不可用：本次只出了结构层结论，字典存在性判定未执行。";
        };
    }

    public static boolean allEmptyTextsDistinct() {
        return java.util.Arrays.stream(EmptyKind.values()).map(UiState::emptyText).distinct().count()
                == EmptyKind.values().length;
    }

    public static String errorBanner(String errorCode, String reason) {
        String action = switch (errorCode == null ? "" : errorCode) {
            case "SOURCE_UNREACHABLE", "CONNECT_FAILED", "TIMEOUT" -> "建议：检查网络后重试，或联系信息科确认接口状态。";
            case "CONFIG_MISSING" -> "建议：检查环境变量配置，补齐缺失变量名。";
            case "DEGRADED_MODE", "MODEL_UNREACHABLE" -> "建议：模型恢复后重新复核；当前提交按钮已禁用。";
            default -> "建议：核对错误码对应的字段规范，必要时联系信息科。";
        };
        return "错误 " + errorCode + "：" + reason + "。" + action;
    }
}
