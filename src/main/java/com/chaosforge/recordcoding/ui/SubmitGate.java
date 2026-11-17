package com.chaosforge.recordcoding.ui;

import com.chaosforge.recordcoding.semantic.SemanticFinding;

import java.util.ArrayList;
import java.util.List;

/** 提交闸门：全部判定已确认 / 每条建议带证据引用 / 确认人时间齐备 / 模型非降级态。 */
public final class SubmitGate {
    public record Review(List<SemanticFinding> findings, boolean degraded, String batchState) {}
    public record GateResult(boolean allowed, List<String> reasons) {
        public String describe() { return allowed ? "允许提交" : String.join("；", reasons); }
    }

    public GateResult check(Review review) {
        List<String> reasons = new ArrayList<>();
        if (review == null) return new GateResult(false, List.of("复核记录为空"));
        if (review.degraded()) reasons.add("模型不可用，当前为降级模式，禁止提交");
        if (review.findings() == null || review.findings().isEmpty()) reasons.add("无可提交的判定");
        else {
            for (SemanticFinding f : review.findings()) {
                if (f.isPromptOnly()) continue;
                if (!f.hasEvidenceRefs()) reasons.add(f.findingId() + " 缺证据引用");
                if (f.confirmedBy().isBlank() || f.confirmedAt().isBlank()) {
                    reasons.add(f.findingId() + " 缺确认人或确认时间");
                }
                if (f.state() == SemanticFinding.ConfirmState.PENDING) {
                    reasons.add(f.findingId() + " 尚未采纳或驳回");
                }
            }
        }
        if ("已提交".equals(review.batchState())) reasons.add("批次已提交，不得重复提交");
        return new GateResult(reasons.isEmpty(), List.copyOf(reasons));
    }
}
