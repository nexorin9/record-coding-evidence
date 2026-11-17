package com.chaosforge.recordcoding.semantic;

import com.chaosforge.recordcoding.modelgw.ModelGateway;
import com.chaosforge.recordcoding.protocol.time.ClinicalInstant;
import com.chaosforge.recordcoding.protocol.time.ClinicalInstantParser;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 首页编码 ↔ 报告事实的语义对拍；报告陈旧与证据缺失走确定性路径，不交给模型。 */
public final class EquivalenceJudge {
    private final ModelGateway gateway;
    private final double threshold;

    public EquivalenceJudge(ModelGateway gateway) { this(gateway, 0.65); }

    public EquivalenceJudge(ModelGateway gateway, double threshold) {
        this.gateway = gateway;
        this.threshold = threshold;
    }

    public double threshold() { return threshold; }

    public Judgement judge(String homeCode, DiagnosticFact fact, String reportTime,
                           String admissionTime, List<String> evidenceRefs) {
        // 确定性路径一：无对应报告证据
        if (fact == null || evidenceRefs == null || evidenceRefs.isEmpty()) {
            return new Judgement(Verdict.EVIDENCE_MISSING, 1.0, List.of(),
                    "无对应报告证据，确定性判为 evidence_missing", false, true);
        }
        // 确定性路径二：报告早于入院即判陈旧
        if (isStale(reportTime, admissionTime)) {
            return new Judgement(Verdict.REPORT_STALE, 1.0, List.copyOf(evidenceRefs),
                    "报告时间早于入院时间，确定性判为 report_stale", false, true);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", "equivalence_judge");
        payload.put("home_code", homeCode);
        payload.put("fact", fact.display());
        payload.put("evidence_refs", evidenceRefs);
        Map<String, Object> response = gateway.chat(payload, "finding");
        String wire = response.get("verdict") == null ? "conflict" : String.valueOf(response.get("verdict"));
        Verdict verdict = Verdict.fromWire(wire);
        double confidence = response.get("confidence") instanceof Number n ? n.doubleValue() : 0.0;
        boolean advise = confidence >= threshold;
        String note = advise ? "模型判定草稿，待人工确认"
                : "置信度 " + confidence + " 低于阈值 " + threshold + "，只提示不生成建议编码";
        return new Judgement(verdict, confidence, List.copyOf(evidenceRefs), note, advise, false);
    }

    public boolean isStale(String reportTime, String admissionTime) {
        ClinicalInstant r = parseFlexible(reportTime);
        ClinicalInstant a = parseFlexible(admissionTime);
        return r != null && a != null && r.isBefore(a);
    }

    private ClinicalInstant parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try { return ClinicalInstantParser.parseHl7Ts(s); } catch (RuntimeException ignored) { /* 换下一种 */ }
        try { return ClinicalInstantParser.parseDicomDt(s); } catch (RuntimeException ignored) { /* 换下一种 */ }
        try {
            OffsetDateTime odt = OffsetDateTime.parse(s);
            return ClinicalInstant.of(odt.toLocalDateTime(), odt.getOffset(),
                    com.chaosforge.recordcoding.protocol.time.InstantPrecision.SECOND);
        } catch (RuntimeException ignored) { /* 换下一种 */ }
        try {
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(s);
            return ClinicalInstant.of(ldt, ZoneId.systemDefault(),
                    com.chaosforge.recordcoding.protocol.time.InstantPrecision.SECOND);
        } catch (RuntimeException ignored) { return null; }
    }
}
