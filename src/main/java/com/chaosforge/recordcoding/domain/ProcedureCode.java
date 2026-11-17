package com.chaosforge.recordcoding.domain;

/** 手术操作编码：码值 / 名称 / 编码系统；缺字段走显式 Missing。 */
public record ProcedureCode(Field<String> code, Field<String> name, CodeSystem system) {
    public static ProcedureCode from(java.util.Map<String, Object> m) {
        String code = com.chaosforge.recordcoding.util.Json.str(m, "code");
        String name = com.chaosforge.recordcoding.util.Json.str(m, "name");
        return new ProcedureCode(Field.of(code, "EMR 未给出手术操作编码"),
                Field.of(name, "EMR 未给出手术操作名称"), CodeSystem.ICD9CM3_MEDICARE);
    }
}
