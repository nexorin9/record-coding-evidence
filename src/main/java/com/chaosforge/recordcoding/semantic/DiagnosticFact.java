package com.chaosforge.recordcoding.semantic;

import com.chaosforge.recordcoding.domain.Field;
import com.chaosforge.recordcoding.util.Json;

import java.util.Map;

/** 报告结论抽取出的六要素；未抽到的要素标缺失而非空串，不得当作「不存在该发现」。 */
public record DiagnosticFact(Field<String> diagnosis, Field<String> site, Field<String> laterality,
                             Field<String> nature, Field<Boolean> negation, Field<Boolean> history,
                             String evidenceRef, double confidence, String scope) {

    @SuppressWarnings("unchecked")
    public static DiagnosticFact fromModel(Map<String, Object> finding, String evidenceRef) {
        Object raw = finding.get("evidence_fact");
        Map<String, Object> fact = raw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Object conf = finding.get("confidence");
        double c = conf instanceof Number n ? n.doubleValue() : 0.0;
        return new DiagnosticFact(
                field(fact, "diagnosis"), field(fact, "site"), field(fact, "laterality"),
                field(fact, "nature"), boolField(fact, "negation"), boolField(fact, "history"),
                evidenceRef, c, "模型抽取");
    }

    public static DiagnosticFact empty(String evidenceRef) {
        return new DiagnosticFact(Field.missing("未抽到诊断"), Field.missing("未抽到部位"),
                Field.missing("未抽到侧别"), Field.missing("未抽到性质"),
                Field.missing("未抽到否定"), Field.missing("未抽到既往"),
                evidenceRef, 0.0, "缺证据");
    }

    private static Field<String> field(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null || String.valueOf(v).isBlank()) return Field.missing("未抽到 " + key);
        return Field.present(String.valueOf(v));
    }

    private static Field<Boolean> boolField(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return Field.present(b);
        if (v instanceof String s && ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s))) {
            return Field.present(Boolean.parseBoolean(s));
        }
        return Field.missing("未抽到 " + key);
    }

    public DiagnosticFact withScope(String newScope) {
        return new DiagnosticFact(diagnosis, site, laterality, nature, negation, history,
                evidenceRef, confidence, newScope);
    }

    public boolean isNegated() { return negation.isPresent() && negation.value(); }
    public boolean isHistory() { return history.isPresent() && history.value(); }
    public boolean hasNoSite() { return site.isMissing(); }
    public boolean hasNoLaterality() { return laterality.isMissing(); }
    public String display() {
        return "诊断=" + diagnosis.display() + " 部位=" + site.display() + " 侧别=" + laterality.display()
                + " 性质=" + nature.display() + " 否定=" + negation.display() + " 既往=" + history.display();
    }
}
