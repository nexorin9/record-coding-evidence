package com.chaosforge.recordcoding.domain;

/** 诊断编码：码值 / 名称 / 编码系统；缺字段走显式 Missing。 */
public record DiagnosisCode(Field<String> code, Field<String> name, CodeSystem system) {
    public static DiagnosisCode from(java.util.Map<String, Object> m) {
        String code = com.chaosforge.recordcoding.util.Json.str(m, "code");
        String name = com.chaosforge.recordcoding.util.Json.str(m, "name");
        return new DiagnosisCode(Field.of(code, "EMR 未给出诊断编码"),
                Field.of(name, "EMR 未给出诊断名称"), CodeSystem.ICD10_MEDICARE);
    }

    public static DiagnosisCode missing() {
        return new DiagnosisCode(Field.missing("首页无主要诊断"), Field.missing("首页无主要诊断名称"),
                CodeSystem.ICD10_MEDICARE);
    }
}
