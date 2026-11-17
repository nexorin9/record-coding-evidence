package com.chaosforge.recordcoding.domain;

import com.chaosforge.recordcoding.util.Json;

import java.util.Map;

/** 病历与病案首页：字段名与 spec.md §2.3 逐项对应；缺字段显式记缺失。 */
public final class MedicalRecord {
    private final Field<String> visitNo;
    private final Field<String> patientId;
    private final Field<String> gender;
    private final Field<Integer> age;
    private final Field<String> admissionTime;
    private final Field<String> dischargeTime;
    private final HomePageCoding coding;

    public MedicalRecord(Field<String> visitNo, Field<String> patientId, Field<String> gender,
                         Field<Integer> age, Field<String> admissionTime, Field<String> dischargeTime,
                         HomePageCoding coding) {
        this.visitNo = visitNo;
        this.patientId = patientId;
        this.gender = gender;
        this.age = age;
        this.admissionTime = admissionTime;
        this.dischargeTime = dischargeTime;
        this.coding = coding;
    }

    public static MedicalRecord fromEmr(Map<String, Object> m) {
        Integer age = null;
        Object a = m.get("age");
        if (a instanceof Number n) age = n.intValue();
        else if (a instanceof String s && !s.isBlank()) {
            try { age = Integer.valueOf(s.trim()); } catch (NumberFormatException ignored) { /* 保持缺失 */ }
        }
        return new MedicalRecord(
                Field.of(Json.str(m, "visit_no"), "EMR 缺 visit_no"),
                Field.of(Json.str(m, "patient_id"), "EMR 缺 patient_id"),
                Field.of(Json.str(m, "gender"), "EMR 缺 gender"),
                Field.of(age, "EMR 缺 age"),
                Field.of(Json.str(m, "admission_time"), "EMR 缺 admission_time"),
                Field.of(Json.str(m, "discharge_time"), "EMR 缺 discharge_time"),
                HomePageCoding.fromEmr(m));
    }

    public Field<String> visitNo() { return visitNo; }
    public Field<String> patientId() { return patientId; }
    public Field<String> gender() { return gender; }
    public Field<Integer> age() { return age; }
    public Field<String> admissionTime() { return admissionTime; }
    public Field<String> dischargeTime() { return dischargeTime; }
    public HomePageCoding coding() { return coding; }
}
