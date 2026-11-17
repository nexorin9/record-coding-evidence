package com.chaosforge.recordcoding.domain;

import com.chaosforge.recordcoding.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 病案首页诊断 / 手术操作编码集合。 */
public final class HomePageCoding {
    private final DiagnosisCode mainDiagnosis;
    private final List<DiagnosisCode> otherDiagnoses;
    private final List<ProcedureCode> procedures;

    public HomePageCoding(DiagnosisCode mainDiagnosis, List<DiagnosisCode> otherDiagnoses,
                          List<ProcedureCode> procedures) {
        this.mainDiagnosis = mainDiagnosis;
        this.otherDiagnoses = List.copyOf(otherDiagnoses);
        this.procedures = List.copyOf(procedures);
    }

    @SuppressWarnings("unchecked")
    public static HomePageCoding fromEmr(Map<String, Object> record) {
        Object mainRaw = record.get("main_diagnosis");
        DiagnosisCode main = mainRaw instanceof Map<?, ?> m
                ? DiagnosisCode.from((Map<String, Object>) m) : DiagnosisCode.missing();
        List<DiagnosisCode> others = new ArrayList<>();
        if (record.get("other_diagnosis") instanceof List<?> list) {
            for (Object o : list) if (o instanceof Map<?, ?> m) others.add(DiagnosisCode.from((Map<String, Object>) m));
        }
        List<ProcedureCode> procs = new ArrayList<>();
        Object p = record.get("procedure");
        if (p instanceof Map<?, ?> m) procs.add(ProcedureCode.from((Map<String, Object>) m));
        else if (p instanceof List<?> list) {
            for (Object o : list) if (o instanceof Map<?, ?> m) procs.add(ProcedureCode.from((Map<String, Object>) m));
        }
        return new HomePageCoding(main, others, procs);
    }

    public DiagnosisCode mainDiagnosis() { return mainDiagnosis; }
    public List<DiagnosisCode> otherDiagnoses() { return otherDiagnoses; }
    public List<ProcedureCode> procedures() { return procedures; }

    public List<String> allDiagnosisCodes() {
        List<String> out = new ArrayList<>();
        if (mainDiagnosis.code().isPresent()) out.add(mainDiagnosis.code().value());
        for (DiagnosisCode d : otherDiagnoses) if (d.code().isPresent()) out.add(d.code().value());
        return out;
    }

    public String mainCodeText() { return mainDiagnosis.code().display(); }
}
