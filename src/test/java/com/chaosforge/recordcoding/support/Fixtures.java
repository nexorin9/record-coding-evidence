package com.chaosforge.recordcoding.support;

import com.chaosforge.recordcoding.util.Json;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 测试夹具：单测边界样例，全部为合成数据，不含任何患者可识别信息。 */
public final class Fixtures {
    private Fixtures() {}

    /** 按 1-based 字段号显式构造 HL7 段，避免人工数竖线导致字段位漂移。 */
    public static String segment(String name, int fieldCount, Map<Integer, String> values) {
        String[] f = new String[fieldCount + 1];
        Arrays.fill(f, "");
        f[0] = name;
        values.forEach((k, v) -> f[k] = v);
        return String.join("|", f);
    }

    /** 最小 ORU^R01：MSH-10 / PID-3 / PV1-19 / OBR-25 / OBX-5 / OBX-14 全部显式定位。 */
    public static String oru() {
        String msh = "MSH|^~\\&|RIS|HOSP|EMR|HOSP|20260202101500||ORU^R01|MSG-TEST-001|P|2.5";
        String pid = segment("PID", 8, Map.of(3, "PID-TEST", 5, "去标识患者", 8, "M"));
        String pv1 = segment("PV1", 19, Map.of(19, "VISIT-TEST"));
        String obr = segment("OBR", 25, Map.of(2, "ORD-TEST", 3, "ACC-TEST", 4, "CT^胸部 CT", 25, "F"));
        String obx = segment("OBX", 14, Map.of(3, "REPORT^影像报告", 5,
                "右下肺实变影，考虑感染性病变", 11, "F", 14, "20260202103000"));
        return String.join("\r", msh, pid, pv1, obr, obx);
    }

    public static String mshLine() {
        return "MSH|^~\\&|RIS|HOSP|EMR|HOSP|20260202101500||ORU^R01|MSG-TEST-001|P|2.5";
    }

    public static String dicomJson() {
        return "{\"00100020\":{\"Value\":[\"PID-000001\"],\"vr\":\"LO\"},"
                + "\"00080020\":{\"Value\":[\"20260202\"],\"vr\":\"DA\"},"
                + "\"00080060\":{\"Value\":[\"CT\"],\"vr\":\"CS\"},"
                + "\"00081032\":{\"Value\":[{\"00080100\":{\"Value\":[\"332400\"],\"vr\":\"SH\"},"
                + "\"00080104\":{\"Value\":[\"胸腔镜下肺楔形切除\"],\"vr\":\"LO\"}}],\"vr\":\"SQ\"}}";
    }

    public static String emrJson() {
        return "{\"visit_no\":\"VISIT-TEST\",\"patient_id\":\"PID-TEST\",\"gender\":\"男\",\"age\":56,"
                + "\"admission_time\":\"20260202090000\",\"discharge_time\":\"20260204160000\","
                + "\"main_diagnosis\":{\"code\":\"J18900\",\"name\":\"社区获得性肺炎\"},"
                + "\"other_diagnosis\":[{\"code\":\"E11900\",\"name\":\"2 型糖尿病\"}],"
                + "\"procedure\":{\"code\":\"332400\",\"name\":\"胸腔镜下肺楔形切除\"}}";
    }

    /** QIDO 投影后的扁平证据行（与 QidoRsClient.fetch 输出同形）。 */
    public static Map<String, Object> qidoFlat() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("00100020", "PID-TEST");
        m.put("0020000D", "1.2.840.10008.5.1.4.2026.1");
        m.put("00080050", "ACC-TEST");
        m.put("00080060", "CT");
        m.put("0008103E", "胸部 CT 平扫");
        return m;
    }

    /** 满足 finding-schema-v1 的结构化输出。 */
    public static Map<String, Object> schemaPayload() {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("diagnosis", "社区获得性肺炎");
        fact.put("site", "右下肺");
        fact.put("laterality", "右");
        fact.put("nature", "感染性病变");
        fact.put("negation", false);
        fact.put("history", false);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("finding_id", "F1");
        m.put("home_code", "J18900");
        m.put("home_code_text", "社区获得性肺炎");
        m.put("evidence_fact", fact);
        m.put("verdict", "conflict");
        m.put("attribution", "side_missing");
        m.put("confidence", 0.91);
        m.put("evidence_refs", List.of("E1"));
        return m;
    }

    public static Map<String, Object> factPayload() {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("diagnosis", "社区获得性肺炎");
        fact.put("site", "右下肺");
        fact.put("laterality", "右");
        fact.put("nature", "感染性病变");
        fact.put("negation", false);
        fact.put("history", false);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidence_fact", fact);
        return m;
    }

    /** 把结构化输出当作 OpenAI-compatible 的 message.content 字符串包进响应。 */
    public static String chatCompletion(String contentJson) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", contentJson);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("choices", List.of(choice));
        root.put("usage", Map.of("total_tokens", 42));
        return Json.stringify(root);
    }
}
