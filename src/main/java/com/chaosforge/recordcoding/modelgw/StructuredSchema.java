package com.chaosforge.recordcoding.modelgw;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 固定结构化输出契约：业务层不直接拼厂商请求体。 */
public final class StructuredSchema {
    public static final String VERSION = "finding-schema-v1";
    public static final List<String> REQUIRED_FIELDS = List.of(
            "finding_id", "home_code", "home_code_text", "evidence_fact", "verdict",
            "attribution", "confidence", "evidence_refs");
    public static final List<String> FACT_FIELDS = List.of(
            "diagnosis", "site", "laterality", "nature", "negation", "history");
    public static final List<String> VERDICTS = List.of(
            "equivalent", "conflict", "report_stale", "evidence_missing");

    public record SchemaResult(boolean valid, List<String> problems) {
        public String describe() { return valid ? "结构合格" : String.join("；", problems); }
    }

    private StructuredSchema() {}

    public static SchemaResult validate(Map<String, Object> payload) {
        List<String> problems = new java.util.ArrayList<>();
        if (payload == null) return new SchemaResult(false, List.of("载荷为空"));
        for (String f : REQUIRED_FIELDS) if (!payload.containsKey(f)) problems.add("缺字段 " + f);
        Object fact = payload.get("evidence_fact");
        if (fact instanceof Map<?, ?> m) {
            for (String f : FACT_FIELDS) if (!m.containsKey(f)) problems.add("evidence_fact 缺 " + f);
        } else if (payload.containsKey("evidence_fact")) {
            problems.add("evidence_fact 类型不符（应为对象）");
        }
        Object verdict = payload.get("verdict");
        if (verdict != null && !(verdict instanceof String s && VERDICTS.contains(s))) {
            problems.add("verdict 枚举越界：" + verdict);
        }
        Object confidence = payload.get("confidence");
        if (confidence != null && !(confidence instanceof Number)) problems.add("confidence 类型不符（应为数字）");
        Object refs = payload.get("evidence_refs");
        if (refs != null && !(refs instanceof List<?>)) problems.add("evidence_refs 类型不符（应为数组）");
        return new SchemaResult(problems.isEmpty(), List.copyOf(problems));
    }

    /** OpenAI-compatible response_format 使用的 JSON Schema。 */
    public static String jsonSchema() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        for (String f : REQUIRED_FIELDS) {
            Map<String, Object> p = new LinkedHashMap<>();
            if (f.equals("evidence_fact")) {
                p.put("type", "object");
                Map<String, Object> fp = new LinkedHashMap<>();
                for (String ff : FACT_FIELDS) fp.put(ff, Map.of("type", "string"));
                p.put("properties", fp);
                p.put("required", FACT_FIELDS);
            } else if (f.equals("confidence")) {
                p.put("type", "number");
            } else if (f.equals("evidence_refs")) {
                p.put("type", "array");
                p.put("items", Map.of("type", "string"));
            } else if (f.equals("verdict")) {
                p.put("type", "string");
                p.put("enum", VERDICTS);
            } else {
                p.put("type", "string");
            }
            props.put(f, p);
        }
        root.put("properties", props);
        root.put("required", REQUIRED_FIELDS);
        root.put("additionalProperties", false);
        return com.chaosforge.recordcoding.util.Json.stringify(root);
    }
}
