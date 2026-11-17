package com.chaosforge.recordcoding.service;

import com.chaosforge.recordcoding.semantic.SemanticFinding;
import com.chaosforge.recordcoding.transport.QcReceipt;
import com.chaosforge.recordcoding.transport.QcReviewSink;
import com.chaosforge.recordcoding.ui.SubmitGate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 确认回写闭环：模型不可达即降级为全量人工复核，网关层二次闸门禁止任何 QC 写请求。 */
public final class CodingAuditService {
    public record SubmitResult(boolean submitted, QcReceipt receipt, List<String> reasons) {}

    private final QcReviewSink sink;
    private final SubmitGate gate = new SubmitGate();
    private boolean degraded;
    private String degradeReason = "";

    public CodingAuditService(QcReviewSink sink) { this.sink = sink; }

    public void markModelUnavailable(String errorCode) {
        degraded = true;
        degradeReason = "模型不可用（" + errorCode + "），当前为降级模式：结构校验 + 证据清单 + 全量人工复核";
    }

    public void clearDegraded() { degraded = false; degradeReason = ""; }
    public boolean isDegraded() { return degraded; }
    public String degradeReason() { return degradeReason; }

    public SubmitResult submit(String visitNo, String batchId, String evidenceHash,
                               List<SemanticFinding> findings, String batchState) {
        SubmitGate.GateResult g = gate.check(new SubmitGate.Review(findings, degraded, batchState));
        if (!g.allowed()) return new SubmitResult(false, null, g.reasons());
        // 网关层二次闸门：降级态直接拒绝，绝不发请求
        if (degraded) {
            return new SubmitResult(false, null, List.of("降级态强行调用 submit 被拒：未发出 QC 写请求"));
        }
        List<Map<String, Object>> body = new ArrayList<>();
        for (SemanticFinding f : findings) {
            if (f.isPromptOnly()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("finding_id", f.findingId());
            m.put("home_code", f.homeCode());
            m.put("code_system", "ICD10_医保版");
            m.put("suggested_codes", f.suggestedCodes());
            m.put("verdict", f.verdict().wire());
            m.put("attribution", f.attribution().wire());
            m.put("evidence_refs", f.evidenceRefs());
            m.put("confirmed_by", f.confirmedBy());
            m.put("confirmed_at", f.confirmedAt());
            body.add(m);
        }
        QcReceipt receipt = sink.submit(visitNo, batchId, body, evidenceHash);
        return new SubmitResult(true, receipt, List.of());
    }
}
