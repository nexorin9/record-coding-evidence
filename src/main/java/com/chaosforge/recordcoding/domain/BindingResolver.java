package com.chaosforge.recordcoding.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 三方实体绑定：多 PatientID → ambiguous，零命中 → not_found，一律拒绝出具判定，绝不取第一条。 */
public final class BindingResolver {

    public VisitBinding resolve(String visitNo, List<Map<String, String>> qidoStudies) {
        Map<String, List<String>> byPatient = new TreeMap<>();
        for (Map<String, String> study : qidoStudies) {
            String pid = pick(study, "patientId", "00100020");
            if (pid == null || pid.isBlank()) continue;
            String uid = pick(study, "studyInstanceUid", "0020000D");
            byPatient.computeIfAbsent(pid, k -> new ArrayList<>()).add(uid == null ? "" : uid);
        }
        if (byPatient.isEmpty()) {
            return new VisitBinding(visitNo, VisitBinding.Result.NOT_FOUND, "", List.of(), List.of(),
                    "零命中：按住院号 " + visitNo + " 未查到任何 PatientID");
        }
        if (byPatient.size() > 1) {
            return new VisitBinding(visitNo, VisitBinding.Result.AMBIGUOUS, "", List.of(),
                    new ArrayList<>(byPatient.keySet()),
                    "多 PatientID 歧义：" + String.join(" / ", byPatient.keySet()));
        }
        String pid = byPatient.keySet().iterator().next();
        return new VisitBinding(visitNo, VisitBinding.Result.RESOLVED, pid,
                byPatient.get(pid), List.of(pid), "");
    }

    /** 反向校验：证据行 PatientID 与绑定结果不一致时判冲突并拒绝出具判定。 */
    public boolean conflicts(String evidencePatientId, VisitBinding binding) {
        return binding.result() != VisitBinding.Result.RESOLVED
                || evidencePatientId == null
                || !binding.patientId().equals(evidencePatientId);
    }

    private static String pick(Map<String, String> study, String... keys) {
        for (String k : keys) {
            String v = study.get(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    public static Map<String, String> study(String patientId, String studyUid, String accession) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("patientId", patientId);
        m.put("studyInstanceUid", studyUid);
        m.put("accessionNumber", accession);
        return m;
    }
}
