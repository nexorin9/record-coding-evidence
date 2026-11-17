package com.chaosforge.recordcoding.evidence;

import com.chaosforge.recordcoding.transport.EvidenceTags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨源证据归一（核心差异化）：QIDO 元数据行与 HL7 报告结论行落到同一张证据行，
 * 统一 evidence_id / 来源标记 / 哈希 / missing 语义；按检查标识与访问号对齐，不做跨源隐式时间对齐。
 */
public final class EvidenceAssembler {

    public EvidenceSnapshot assemble(String visitNo, String batchId,
                                     List<Map<String, Object>> qidoStudies,
                                     List<Map<String, String>> hl7Reports) {
        List<EvidenceRow> rows = new ArrayList<>();
        for (int i = 0; i < qidoStudies.size(); i++) {
            Map<String, Object> study = qidoStudies.get(i);
            String accession = text(study.get(EvidenceTags.ACCESSION_NUMBER));
            if (accession.isBlank() || "missing".equals(accession)) accession = "ACC-" + i;
            for (String tag : EvidenceTags.ALL) {
                Object raw = study.get(tag);
                String v = raw == null ? "" : String.valueOf(raw);
                if (v.isBlank() || "missing".equals(v)) {
                    rows.add(EvidenceRow.missing(EvidenceType.STUDY_METADATA, EvidenceSource.QIDO_RS,
                            tagLabel(tag), tag, MissingReason.FIELD_ABSENT, accession));
                } else {
                    rows.add(EvidenceRow.present(EvidenceType.STUDY_METADATA, EvidenceSource.QIDO_RS,
                            tagLabel(tag), tag, v, accession));
                }
            }
        }
        // 按访问号对齐报告行；未命中检查时仍保留报告行并显式标缺口
        for (Map<String, String> report : hl7Reports) {
            String accession = text(report.get("accessionNumber"));
            if (accession.isBlank()) accession = "UNASSIGNED";
            for (Map.Entry<String, String> e : report.entrySet()) {
                if (e.getKey().equals("accessionNumber") || e.getKey().equals("controlId")) continue;
                String v = e.getValue();
                if (v == null || v.isBlank()) {
                    rows.add(EvidenceRow.missing(EvidenceType.REPORT_CONCLUSION, EvidenceSource.HL7_MLLP,
                            e.getKey(), e.getKey(), MissingReason.EMPTY_VALUE, accession));
                } else {
                    rows.add(EvidenceRow.present(EvidenceType.REPORT_CONCLUSION, EvidenceSource.HL7_MLLP,
                            e.getKey(), e.getKey(), v, accession));
                }
            }
            rows.add(EvidenceRow.present(EvidenceType.REPORT_CONCLUSION, EvidenceSource.HL7_MLLP,
                    "访问号对齐键", "OBR-3/OBR-4", accession, accession));
        }
        return new EvidenceSnapshot(visitNo, batchId, rows, EvidenceHash.of(rows));
    }

    /** 仅按检查标识与访问号对齐，绝不按时间做隐式跨源合并。 */
    public Map<String, List<EvidenceRow>> groupByAccession(EvidenceSnapshot snapshot) {
        Map<String, List<EvidenceRow>> groups = new LinkedHashMap<>();
        for (EvidenceRow r : snapshot.rows()) groups.computeIfAbsent(r.accession(), k -> new ArrayList<>()).add(r);
        return groups;
    }

    public EvidenceSnapshot fromRows(String visitNo, String batchId, List<EvidenceRow> rows) {
        return new EvidenceSnapshot(visitNo, batchId, rows, EvidenceHash.of(rows));
    }

    private static String text(Object o) { return o == null ? "" : String.valueOf(o); }

    private static String tagLabel(String tag) {
        return switch (tag) {
            case EvidenceTags.PATIENT_ID -> "患者标识";
            case EvidenceTags.STUDY_INSTANCE_UID -> "检查实例 UID";
            case EvidenceTags.SERIES_INSTANCE_UID -> "序列实例 UID";
            case EvidenceTags.ACCESSION_NUMBER -> "检查号";
            case EvidenceTags.STUDY_ID -> "检查号/申请号";
            case EvidenceTags.SERIES_DESCRIPTION -> "序列描述";
            case EvidenceTags.MODALITY -> "模态";
            case EvidenceTags.STUDY_DATE -> "检查日期";
            case EvidenceTags.STUDY_TIME -> "检查时间";
            case EvidenceTags.PROCEDURE_CODE_SEQUENCE -> "手术操作编码序列";
            default -> tag;
        };
    }
}
