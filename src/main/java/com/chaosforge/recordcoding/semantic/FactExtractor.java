package com.chaosforge.recordcoding.semantic;

import com.chaosforge.recordcoding.modelgw.GatewayException;
import com.chaosforge.recordcoding.modelgw.ModelGateway;
import com.chaosforge.recordcoding.modelgw.StructuredSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 报告结论自由文本 → 结构化要素；模型承担抽取，否定与既往的跨句识别单独覆盖。 */
public final class FactExtractor {
    private final ModelGateway gateway;

    public FactExtractor(ModelGateway gateway) { this.gateway = gateway; }

    public Map<String, Object> payload(String reportText, String evidenceRef) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("task", "fact_extraction");
        p.put("report_text", reportText);
        p.put("evidence_ref", evidenceRef);
        p.put("scope", scopeOf(reportText));
        // 单句与跨句两种范围分别给模型明确提示，避免词表匹配漏掉跨句否定 / 既往
        p.put("hints", List.of(
                "否定表述如「未见明显异常」需标 negation=true",
                "既往表述如「术前已诊断」需标 history=true",
                "跨句否定（上一句结论 + 下一句未见）需按全文范围判定"));
        return p;
    }

    /** 单句 / 跨句范围识别：按中文句号与分号切分，≥2 句即跨句范围。 */
    public String scopeOf(String reportText) {
        if (reportText == null) return "单句";
        long sentences = reportText.chars().filter(c -> c == '。' || c == '；' || c == '\n').count();
        return sentences >= 1 ? "跨句" : "单句";
    }

    public DiagnosticFact extract(String reportText, String evidenceRef) {
        Map<String, Object> p = payload(reportText, evidenceRef);
        try {
            Map<String, Object> response = gateway.chat(p, "fact");
            return DiagnosticFact.fromModel(response, evidenceRef).withScope(scopeOf(reportText));
        } catch (GatewayException e) {
            if (e.code() == GatewayException.ErrorCode.STRUCTURE_INVALID) {
                throw new FactExtractionException("MODEL_UNPARSED",
                        "要素抽取结构校验失败，按人工全量复核处理：" + e.getMessage());
            }
            throw e;
        }
    }

    public static final class FactExtractionException extends RuntimeException {
        private final String code;
        public FactExtractionException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
